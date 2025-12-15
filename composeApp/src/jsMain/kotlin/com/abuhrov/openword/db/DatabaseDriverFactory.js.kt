package com.abuhrov.openword.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import com.abuhrov.openword.jsDatabaseCache
import org.khronos.webgl.Uint8Array
import org.w3c.dom.Worker
import kotlin.js.Promise
import kotlinx.coroutines.await

actual class DatabaseDriverFactory {
    actual suspend fun createDriver(dbName: String): SqlDriver {
        // STRATEGY: Use a Classic Worker that loads sql.js from CDN.
        // This avoids "import statement outside a module" errors completely.

        val workerScript = """
            // 1. Load SQL.js (Classic Script) from CDN
            importScripts("https://cdnjs.cloudflare.com/ajax/libs/sql.js/1.8.0/sql-wasm.js");

            var db = null;

            // 2. Initialize SQL.js
            initSqlJs({
                // Locate the WASM binary (must match version above)
                locateFile: file => "https://cdnjs.cloudflare.com/ajax/libs/sql.js/1.8.0/sql-wasm.wasm"
            }).then(function(SQL) {
                // SQL is ready
                self.SQL = SQL;
                postMessage({ id: 'ready' });
            });

            // 3. Handle Messages (SQLDelight Protocol + Custom Load)
            self.onmessage = function(event) {
                var data = event.data;
                
                // A. Custom 'load_db' Action
                if (data && data.action === 'load_db') {
                    if (!self.SQL) {
                        self.postMessage({ id: data.id, error: "SQL.js not ready" });
                        return;
                    }
                    try {
                        // Create DB from the byte array
                        db = new self.SQL.Database(data.buffer);
                        self.postMessage({ id: data.id, success: true });
                    } catch (e) {
                        self.postMessage({ id: data.id, error: e.toString() });
                    }
                }
                
                // B. SQLDelight Standard Queries
                else if (data && data.id) {
                    if (!db) {
                        self.postMessage({ id: data.id, error: "Database not open" });
                        return;
                    }
                    
                    try {
                        // Handle 'exec' (Write) or 'query' (Read)
                        // This is a minimal implementation of the SQLDelight worker protocol
                        if (data.action === 'exec') {
                            db.run(data.sql, data.params);
                            self.postMessage({ id: data.id, results: [] }); // return empty on success
                        } else if (data.action === 'query') {
                            var stmt = db.prepare(data.sql);
                            stmt.bind(data.params);
                            var rows = [];
                            while (stmt.step()) {
                                rows.push(stmt.get());
                            }
                            stmt.free();
                            self.postMessage({ id: data.id, results: { values: rows } });
                        } else if (data.action === 'beginTransaction') {
                            db.run("BEGIN TRANSACTION");
                            self.postMessage({ id: data.id });
                        } else if (data.action === 'commitTransaction') {
                            db.run("COMMIT");
                            self.postMessage({ id: data.id });
                        } else if (data.action === 'rollbackTransaction') {
                            db.run("ROLLBACK");
                            self.postMessage({ id: data.id });
                        } else {
                            // unknown
                            self.postMessage({ id: data.id, error: "Unknown action: " + data.action });
                        }
                    } catch (e) {
                        self.postMessage({ id: data.id, error: e.toString() });
                    }
                }
            };
        """

        val blob = org.w3c.files.Blob(arrayOf(workerScript), org.w3c.files.BlobPropertyBag(type = "application/javascript"))
        val blobUrl = org.w3c.dom.url.URL.createObjectURL(blob)

        // Create Classic Worker (default)
        val worker = Worker(blobUrl)

        // Wait for 'ready' message from initSqlJs
        waitForWorkerReady(worker).await()

        val dbBytes = jsDatabaseCache[dbName]
        if (dbBytes != null) {
            initWorker(worker, dbName, dbBytes).await()
        } else {
            // Initialize empty DB if no file found
            initWorker(worker, dbName, Uint8Array(0)).await()
        }

        return WebWorkerDriver(worker)
    }
}

private fun waitForWorkerReady(worker: Worker): Promise<Boolean> = js("""
    new Promise(function(resolve, reject) {
        var handler = function(event) {
            if (event.data && event.data.id === 'ready') {
                worker.removeEventListener('message', handler);
                resolve(true);
            }
        };
        worker.addEventListener('message', handler);
    })
""")

private fun initWorker(worker: Worker, name: String, data: Uint8Array): Promise<Boolean> = js("""
    new Promise(function(resolve, reject) {
        var id = Math.random().toString();
        
        var timeout = setTimeout(function() {
            worker.removeEventListener('message', handler);
            reject(new Error("Worker timeout waiting for DB load: " + name));
        }, 15000);

        var handler = function(event) {
            if (event.data.id === id) {
                clearTimeout(timeout);
                worker.removeEventListener('message', handler);
                if (event.data.error) {
                    reject(new Error(event.data.error));
                } else {
                    resolve(true);
                }
            }
        };
        
        worker.addEventListener('message', handler);
        worker.postMessage({ action: 'load_db', id: id, name: name, buffer: data });
    })
""")