import { Component, ElementRef, ViewChild, signal } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms'; // ✅ ADD THIS
import { ChangeDetectorRef } from '@angular/core';
import { computed } from '@angular/core';


@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule], // ✅ ADD HERE
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  Object = Object;
  activeTab = signal<'extracted' | 'chat' | 'api'>('extracted');

  selectedFileName = signal<string | null>(null);
  private _selectedFileUrl: SafeResourceUrl | null = null;

  sessionId = signal<string | null>(null);

  classification = signal<any>(null);
  extractedData = signal<any>(null);

  chatMessages = signal<any[]>([]);
  chatInput = signal<string>('');
  copiedResponse = signal(false);
  copiedRequest = signal(false);
  copiedEndpoint = signal(false);
  isLoading = signal(false);
  isUploading = signal(false);     // upload in progress
  isExtracting = signal(false);    // after sessionId, before extract done
  isDragging = signal(false);
  isThinking = signal(false);
  isCollapsed = signal(false);
  bulkMode = signal(false);
  selectedFile = signal<any | null>(null);
selectedDocType = signal('Invoice');
classMap = signal<{ [key: string]: any[] }>({});
selectedClass = signal<string | null>(null);
selectedFileIndex = signal<number>(0);
sessionDataMap = signal<{ [sessionId: string]: any }>({});
selectedSessionId = signal<string | null>(null);

bulkFiles = signal<any[]>([]);
/*
[
  {
    file: File,
    name: string,
    session_id: string | null,
    status: 'pending' | 'processing' | 'done',
    data: any
  }
]
*/

selectedBulkIndex = signal(0);
bulkProgress = signal(0);
isBulkProcessing = signal(false);
  entries() {
  // 🟢 BULK MODE
  if (this.bulkMode()) {
    const sessionId = this.selectedSessionId();
    const data = this.sessionDataMap()[sessionId || '']?.data;

    return data ? Object.entries(data) : [];
  }

  // 🔵 SINGLE MODE
  return Object.entries(this.extractedData() || {});
}

  @ViewChild('fileInput') fileInput?: ElementRef<HTMLInputElement>;
  @ViewChild('chatContainer') chatContainer?: ElementRef;

  constructor(private sanitizer: DomSanitizer,private cdr: ChangeDetectorRef) {}

  // ---------------- TAB ----------------
  setTab(tab: 'extracted' | 'chat' | 'api') {
    this.activeTab.set(tab);
  }

  // ---------------- FILE UPLOAD ----------------
  onUploadClick() {
    this.fileInput?.nativeElement.click();
  }

  async onFileSelected(event: Event) {
  const input = event.target as HTMLInputElement;
  const files = input.files ? Array.from(input.files) : [];

  if (!files.length) return;

  // 🟢 BULK MODE
  if (this.bulkMode()) {

    const mapped = files.map((f: File) => ({
      file: f,
      name: f.name,
      session_id: null,
      status: 'pending',
      data: null
    }));
    this.resetAllState();

    this.bulkFiles.set(mapped);

    // optional: clear preview (since bulk mode)
    this.selectedFileName.set('');
    this._selectedFileUrl = null;

    input.value = '';

    // 🔥 start bulk processing
    this.processBulk();

    return;
  }

  // 🔵 SINGLE FILE (existing logic untouched)
  const file = files[0];

  this.selectedFileName.set(file.name);

  const objectUrl = URL.createObjectURL(file);
  this._selectedFileUrl =
    this.sanitizer.bypassSecurityTrustResourceUrl(objectUrl);

  input.value = '';

  // 🔥 existing backend call
  this.handleFile(file);
}
async processBulk() {
  this.isLoading.set(true);
  this.isExtracting.set(true);

  const files = [...this.bulkFiles()];
  const batchSize = 1;

  this.isBulkProcessing.set(true);
  this.bulkProgress.set(0);

  let processed = 0;

  for (let i = 0; i < files.length; i += batchSize) {
    const batch = files.slice(i, i + batchSize);

    await Promise.all(
      batch.map(async (item, indexInBatch) => {
        const globalIndex = i + indexInBatch;

        // 🔄 STEP 1: MARK FILE AS PROCESSING (ONLY SOURCE OF TRUTH)
        files[globalIndex].status = 'processing';
        this.bulkFiles.set([...files]);

        const formData = new FormData();
        formData.append('file', item.file);

        try {
          // 🔹 UPLOAD
          const uploadRes: any = await fetch(
            'http://127.0.0.1:8000/upload-pdf',
            {
              method: 'POST',
              body: formData
            }
          ).then(r => r.json());

          const sessionId = uploadRes.session_id;
          const classification = uploadRes.classification_result;

          // 🔹 EXTRACT
          const extractRes: any = await fetch(
            'http://127.0.0.1:8000/api/v1/extract',
            {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({ session_id: sessionId })
            }
          ).then(r => r.json());

          const extracted = extractRes.extracted_data;

          // ================================
          // ✅ STORE DATA (MAIN SOURCE)
          // ================================
          const sessionMap = { ...this.sessionDataMap() };

          sessionMap[sessionId] = {
            classification,
            data: extracted,
            fileName: item.file.name
          };

          this.sessionDataMap.set(sessionMap);

          // ================================
          // ✅ ADD TO CLASS MAP (ONLY WHEN DONE)
          // ================================
          const docType = classification?.document_type || 'Others';
          const updatedClassMap = { ...this.classMap() };

          if (!updatedClassMap[docType]) {
            updatedClassMap[docType] = [];
          }

          updatedClassMap[docType].push({
            session_id: sessionId,
            name: item.file.name,
            status: 'done'
          });

          this.classMap.set(updatedClassMap);

          // ================================
          // ✅ UPDATE BULK FILE STATE
          // ================================
          files[globalIndex].status = 'done';
          files[globalIndex].session_id = sessionId;

          this.bulkFiles.set([...files]);

          // ================================
          // 🔥 AUTO SELECT FIRST FILE
          // ================================
          if (!this.selectedSessionId()) {
            this.onSelectFile(sessionId);
          }

        } catch (err) {
          console.error('Bulk error:', err);

          files[globalIndex].status = 'pending';
          this.bulkFiles.set([...files]);
        }

        processed++;

        this.bulkProgress.set(
          Math.floor((processed / files.length) * 100)
        );

        this.isLoading.set(false);
        this.isExtracting.set(false);
        
      })
    );
  }
  this.isBulkProcessing.set(false);
}
classKeys() {
  return Object.keys(this.classMap() || {});
}
syncSelectedFile(file: any) {
  if (!file) return;
   console.log('SYNC FILE:', file);

  this.sessionId.set(file.session_id);
  this.classification.set(file.classification);
  this.extractedData.set(file.data);

  this.resetChat();
}
  selectedFileUrl(): SafeResourceUrl | null {
    return this._selectedFileUrl;
  }

  // ---------------- API CALLS ----------------

  async uploadPdf(file: File) {
  this.isLoading.set(true);   // ✅ START LOADER
  this.resetChat();

  const formData = new FormData();
  formData.append('file', file);

  try {
    const res: any = await fetch('http://127.0.0.1:8000/upload-pdf', {
      method: 'POST',
      body: formData
    });

    const data = await res.json();

    this.sessionId.set(data.session_id);
    this.classification.set(data.classification_result);
    this.isUploading.set(false);
    this.isExtracting.set(true);
    this.isLoading.set(false);

    await this.extractData();

  } catch (err) {
    console.error(err);
    this.isLoading.set(false);
  } finally {
    this.isLoading.set(false);  // ✅ STOP LOADER
  }
}
  async extractData() {
    if (!this.sessionId()) return;

    try {
      const res: any = await fetch('http://127.0.0.1:8000/api/v1/extract', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          session_id: this.sessionId()
        })
      });

      const data = await res.json();

      console.log('EXTRACT RESPONSE:', data);

      queueMicrotask(() => {
  this.extractedData.set(data.extracted_data);
});
this.cdr.detectChanges();

    } catch (err) {
      console.error('Extract error:', err);
    }
    finally {
    // 🔥 stop loader → show data
    this.isExtracting.set(false);
  }
  }

  // ---------------- CHAT ----------------

  async sendMessage() {
  setTimeout(() => this.scrollToBottom(), 50);
  if (!this.chatInput() || !this.activeSessionId()) return;

  const userMsg = this.chatInput();

  this.chatMessages.update(m => [...m, { role: 'user', text: userMsg }]);
  this.chatInput.set('');

  this.isThinking.set(true);   // 🔥 START THINKING

  try {
    const res: any = await fetch('http://127.0.0.1:8000/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        session_id: this.activeSessionId(),
        query: userMsg
      })
    });

    const data = await res.json();

    this.chatMessages.update(m => [
      ...m,
      { role: 'ai', text: data.response }
    ]);

  } catch (err) {
    console.error(err);
  } finally {
    this.isThinking.set(false);  // 🔥 STOP
    setTimeout(() => this.scrollToBottom(), 50);
  }
}
  copyRequest() {
  const text = JSON.stringify({
    session_id: this.activeSessionId() || 'No Session'
  }, null, 2);

  navigator.clipboard.writeText(text).then(() => {
    this.copiedRequest.set(true);

    setTimeout(() => this.copiedRequest.set(false), 1500);
  });
}

copyResponse() {
  const data = this.extractedData();
  if (!data) return;

  const text = JSON.stringify(data, null, 2);

  navigator.clipboard.writeText(text).then(() => {
    this.copiedResponse.set(true);

    // revert after 1.5 sec
    setTimeout(() => this.copiedResponse.set(false), 1500);
  });
}
copyEndpoint() {
  const text = `http://127.0.0.1:8000/api/v1/extract`;

  navigator.clipboard.writeText(text).then(() => {
    this.copiedEndpoint.set(true);

    setTimeout(() => this.copiedEndpoint.set(false), 1500);
  });
}
onDragOver(event: DragEvent) {
  event.preventDefault();
  this.isDragging.set(true);
}

onDragLeave(event: DragEvent) {
  event.preventDefault();
  this.isDragging.set(false);
}

onDrop(event: DragEvent) {
  event.preventDefault();
  this.isDragging.set(false);

  const files = event.dataTransfer?.files;

  if (!files || files.length === 0) return;

  // 🔵 SINGLE MODE
  if (!this.bulkMode()) {
    this.handleFile(files[0]);
    return;
  }

  // 🟢 BULK MODE
  const mapped = Array.from(files).map((f: File) => ({
    file: f,
    name: f.name,
    session_id: null,
    status: 'pending'
  }));

  this.bulkFiles.set(mapped);
  this.processBulk();
}
async handleFile(file: File) {
  this.selectedFileName.set(file.name);

  const objectUrl = URL.createObjectURL(file);
  this._selectedFileUrl =
    this.sanitizer.bypassSecurityTrustResourceUrl(objectUrl);

  await this.uploadPdf(file);
}
scrollToBottom() {
  try {
    const el = this.chatContainer?.nativeElement;
    el.scrollTop = el.scrollHeight;
  } catch {}
}

toggleCollapse() {
  this.isCollapsed.update(v => !v);
}
resetChat() {
  this.chatMessages.set([]);
  this.chatInput.set('');
  this.isThinking.set(false);
  this.sessionId.set('');
  this.activeTab.set('extracted');
  this.isCollapsed.set(false);

  // optional: scroll reset
  setTimeout(() => this.scrollToBottom(), 50);
}
downloadExcel() {
  const data = this.entries();

  if (!data || data.length === 0) return;

  // Create header row (keys)
  const headers = data.map((item: any) => item[0]);

  // Create values row
  const values = data.map((item: any) => {
    // Escape commas and quotes
    const val = item[1] ?? '';
    return `"${String(val).replace(/"/g, '""')}"`;
  });

  // Build CSV content
  const csvContent =
    headers.join(',') + '\n' +
    values.join(',');

  // Create blob
  const blob = new Blob([csvContent], {
    type: 'text/csv;charset=utf-8;'
  });

  // Create download link
  const link = document.createElement('a');
  const url = URL.createObjectURL(blob);

  link.setAttribute('href', url);
  link.setAttribute(
  'download',
  (this.selectedFileName()?.split('.')[0] || 'file') + '_extracted_data.csv'
);
  link.click();

  URL.revokeObjectURL(url);
}
currentClassification() {
  if (this.bulkMode()) {
    const sessionId = this.selectedSessionId();
    return this.sessionDataMap()[sessionId || '']?.classification || null;
  }

  return this.classification();
}
downloadAllExcel() {
  const files = this.bulkFiles();

  if (!files.length) return;

  // ✅ only take completed files
  const completed = files.filter(f => f.status === 'done' && f.data);

  if (!completed.length) return;

  // 🧠 collect all unique keys
  const allKeys = new Set<string>();

  completed.forEach(f => {
    Object.keys(f.data).forEach(k => allKeys.add(k));
  });

  const headers = ['filename', ...Array.from(allKeys)];

  // 🧾 build rows
  const rows: string[] = [];

  // header row
  rows.push(headers.join(','));

  // data rows
  completed.forEach(f => {
    const row = headers.map(h => {
      if (h === 'filename') {
        return `"${f.name.replace(/"/g, '""')}"`;
      }

      const value = f.data?.[h] ?? '';
      return `"${String(value).replace(/"/g, '""')}"`;
    });

    rows.push(row.join(','));
  });

  const csvContent = rows.join('\n');

  // 📦 create file
  const blob = new Blob(["\uFEFF" + csvContent], {
    type: 'text/csv;charset=utf-8;'
  });

  const link = document.createElement('a');
  const url = URL.createObjectURL(blob);

  link.href = url;
  link.download = `bulk_extracted_${Date.now()}.csv`;

  link.click();

  URL.revokeObjectURL(url);
}
activeSessionId() {
  if (this.bulkMode()) {
    return this.selectedSessionId();
  }

  return this.sessionId();
}
onSelectBulkFile(index: number) {
  this.selectedBulkIndex.set(index);

  const file = this.bulkFiles()[index];

  if (!file || file.status !== 'done') return;

  // 🔥 sync with single-file UI state
  this.sessionId.set(file.session_id);
  this.classification.set(file.classification);
  this.extractedData.set(file.data);

  // 🔥 reset chat for new context
  this.resetChat();
}
onSelectFile(sessionId: string) {
  this.selectedSessionId.set(sessionId);

  const data = this.sessionDataMap()[sessionId];

  if (!data) return;

  this.sessionId.set(sessionId);
  this.classification.set(data.classification);
  this.extractedData.set(data.data);

  this.resetChat();
}
onSelectClass(cls: string) {
  this.selectedClass.set(cls);

  const files = this.classMap()[cls];

  if (!files || files.length === 0) return;

  const firstFile = files[0];

  if (!firstFile?.session_id) return;

  // 🔥 THIS IS THE KEY
  this.onSelectFile(firstFile.session_id);
}
onSelectClassFile(cls: string, index: number) {
  this.selectedClass.set(cls);
  this.selectedFileIndex.set(index);

  const file = this.classMap()[cls]?.[index];

  if (!file) return;

  console.log('SELECTED FILE:', file);

  this.selectedFile.set(file); // ✅ MAIN FIX

  // optional (only if APIs still depend on it)
  this.sessionId.set(file.session_id);

  this.resetChat();
}
downloadClassExcel() {
  const cls = this.selectedClass();
  if (!cls) return;

  const files = this.classMap()[cls] || [];

  if (!files.length) return;

  // 🔥 GET DATA FROM sessionDataMap
  const sessions = files
    .map(f => this.sessionDataMap()[f.session_id])
    .filter(Boolean);

  if (!sessions.length) return;

  const keys = new Set<string>();

  sessions.forEach(s => {
    Object.keys(s.data || {}).forEach(k => keys.add(k));
  });

  const headers = ['filename', ...Array.from(keys)];

  const rows = [headers.join(',')];

  sessions.forEach(s => {
    const row = headers.map(h => {
      if (h === 'filename') return `"${s.fileName}"`;

      return `"${(s.data?.[h] || '')
        .toString()
        .replace(/"/g, '""')}"`;
    });

    rows.push(row.join(','));
  });

  const blob = new Blob(["\uFEFF" + rows.join('\n')], {
    type: 'text/csv;charset=utf-8;'
  });

  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = `${cls}_data.csv`;
  link.click();
}
resetAllState() {
  // Core state
  this.sessionId.set(null);
  this.selectedSessionId.set(null);

  this.classification.set(null);
  this.extractedData.set(null);

  // Bulk state
  this.bulkFiles.set([]);
  this.classMap.set({});
  this.sessionDataMap.set({});

  this.selectedClass.set(null);
  this.selectedFileIndex.set(0);
  this.selectedBulkIndex.set(0);

  // UI state
  this.bulkProgress.set(0);
  this.isBulkProcessing.set(false);
  this.isUploading.set(false);
  this.isExtracting.set(false);
  this.isLoading.set(false);

  // File preview
  this.selectedFileName.set(null);
  this._selectedFileUrl = null;

  // Chat
  this.chatMessages.set([]);
  this.chatInput.set('');
  this.isThinking.set(false);

  // Tabs & collapse
  this.activeTab.set('extracted');
  this.isCollapsed.set(false);
}
onToggleBulk() {
  const next = !this.bulkMode();

  // 🔥 RESET EVERYTHING
  this.resetAllState();

  // 🔁 THEN APPLY MODE
  this.bulkMode.set(next);
}
}
