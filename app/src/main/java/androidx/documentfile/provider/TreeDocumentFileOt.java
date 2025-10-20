/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.documentfile.provider;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;
import androidx.annotation.RequiresApi;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
@RequiresApi(21)
public class TreeDocumentFileOt extends DocumentFile {
    private static final String TAG = "DocumentFile";
    private Context mContext;
    private Uri mUri;
    private String mMime;
    private String mName;
    private String mId;
    public TreeDocumentFileOt(@Nullable DocumentFile parent, Context context, Uri uri) {
        super(parent);
        mContext = context;
        mUri = uri;
    }
    public TreeDocumentFileOt(@Nullable DocumentFile parent, Context context, Uri uri, String name, String mime) {
        super(parent);
        mContext = context;
        mUri = uri;
        mMime = mime;
        mName = name;
        int startIndex = name.lastIndexOf("[");
        int endIndex = name.lastIndexOf("]");
        if (startIndex < endIndex) {
            mId = name.substring(startIndex + 1, endIndex);
        }
    }
    @Override
    public @Nullable DocumentFile createFile(@NonNull String mimeType,
                                             @NonNull String displayName) {
        final Uri result = TreeDocumentFileOt.createFile(mContext, mUri, mimeType, displayName);
        return (result != null) ? new TreeDocumentFileOt(this, mContext, result) : null;
    }
    private static @Nullable Uri createFile(Context context, Uri self, String mimeType,
                                            String displayName) {
        try {
            return DocumentsContract.createDocument(context.getContentResolver(), self, mimeType,
                    displayName);
        } catch (Exception e) {
            return null;
        }
    }
    @Override
    public @Nullable DocumentFile createDirectory(@NonNull String displayName) {
        final Uri result = TreeDocumentFileOt.createFile(
                mContext, mUri, DocumentsContract.Document.MIME_TYPE_DIR, displayName);
        return (result != null) ? new TreeDocumentFileOt(this, mContext, result) : null;
    }
    @Override
    public @NonNull Uri getUri() {
        return mUri;
    }
    @Override
    public @Nullable String getName() {
        if (mName != null) {
            return mName;
        } else {
            return DocumentsContractApi19Ot.getName(mContext, mUri);
        }
    }
    public @Nullable String getId() {
        return mId;
    }
    @Override
    public @Nullable String getType() {
        if (mMime != null) {
            return mMime;
        } else {
            return DocumentsContractApi19Ot.getType(mContext, mUri);
        }
    }
    @Override
    public boolean isDirectory() {
        return DocumentsContractApi19Ot.isDirectory(mContext, mUri);
    }
    @Override
    public boolean isFile() {
        return DocumentsContractApi19Ot.isFile(mContext, mUri);
    }
    @Override
    public boolean isVirtual() {
        return DocumentsContractApi19Ot.isVirtual(mContext, mUri);
    }
    @Override
    public long lastModified() {
        return DocumentsContractApi19Ot.lastModified(mContext, mUri);
    }
    @Override
    public long length() {
        return DocumentsContractApi19Ot.length(mContext, mUri);
    }
    @Override
    public boolean canRead() {
        return DocumentsContractApi19Ot.canRead(mContext, mUri);
    }
    @Override
    public boolean canWrite() {
        return DocumentsContractApi19Ot.canWrite(mContext, mUri);
    }
    @Override
    public boolean delete() {
        try {
            return DocumentsContract.deleteDocument(mContext.getContentResolver(), mUri);
        } catch (Exception e) {
            return false;
        }
    }
    @Override
    public boolean exists() {
        return DocumentsContractApi19Ot.exists(mContext, mUri);
    }
    @Override
    public DocumentFile @NonNull [] listFiles() {
        final ContentResolver resolver = mContext.getContentResolver();
        final Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(mUri,
                DocumentsContract.getDocumentId(mUri));
        final ArrayList<Uri> results = new ArrayList<>();
        final ArrayList<String> resultMimes = new ArrayList<>();
        final ArrayList<String> resultNames = new ArrayList<>();
        Cursor c = null;
        try {
            c = resolver.query(childrenUri, new String[] {
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
            }, null, null, null);
            while (c.moveToNext()) {
                final String documentId = c.getString(0);
                final String documentName = c.getString(1);
                final String documentMime = c.getString(2);
                final Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(mUri,
                        documentId);
                results.add(documentUri);
                resultMimes.add(documentMime);
                resultNames.add(documentName);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed query: " + e);
        } finally {
            closeQuietly(c);
        }
        final Uri[] result = results.toArray(new Uri[0]);
        final String[] mime = resultMimes.toArray(new String[0]);
        final String[] name = resultNames.toArray(new String[0]);
        final DocumentFile[] resultFiles = new DocumentFile[result.length];
        for (int i = 0; i < result.length; i++) {
            resultFiles[i] = new TreeDocumentFileOt(this, mContext, result[i], name[i], mime[i]);
        }
        return resultFiles;
    }
    private static void closeQuietly(@Nullable AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (RuntimeException rethrown) {
                throw rethrown;
            } catch (Exception ignored) {
            }
        }
    }
    @Override
    public boolean renameTo(@NonNull String displayName) {
        try {
            final Uri result = DocumentsContract.renameDocument(
                    mContext.getContentResolver(), mUri, displayName);
            if (result != null) {
                mUri = result;
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }
}