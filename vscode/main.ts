'use strict';
import * as VSCode from 'vscode';
import * as Path from 'path';
import * as FS from 'fs';
import * as Net from 'net';
import {LanguageClient, LanguageClientOptions, SettingMonitor, ServerOptions, StreamInfo} from 'vscode-languageclient';

/** Called when extension is activated */
export function activate(context: VSCode.ExtensionContext) {


    // Options to control the language client
    let clientOptions: LanguageClientOptions = {
        // Register the server for java documents
        documentSelector: ['java'],
        synchronize: {
            // Synchronize the setting section 'java' to the server
            // NOTE: this currently doesn't do anything
            configurationSection: 'java',
            fileEvents: [
                VSCode.workspace.createFileSystemWatcher('**/*.java')
            ]
        }
    }

    function createServer(): Promise<StreamInfo> {
        return new Promise((resolve, reject) => {
            console.log("connecting to server...");
            (new Net.Socket()).connect(2088, 'localhost', function() {
                console.log("connected to server");
                resolve({
                    reader: this,
                    writer: this
                });
            });
        });
    }

    // Create the language client and start the client.
    let client = new LanguageClient('java-lsp', createServer, clientOptions);
    let disposable = client.start();

    // Push the disposable to the context's subscriptions so that the 
    // client can be deactivated on extension deactivation
    context.subscriptions.push(disposable);
}

// this method is called when your extension is deactivated
export function deactivate() {
}