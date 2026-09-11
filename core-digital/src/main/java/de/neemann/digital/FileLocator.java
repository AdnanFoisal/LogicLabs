/*
 * Copyright (c) 2019 Helmut Neemann.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital;

import java.io.File;

/**
 * Headless FileLocator helper to resolve relative files without desktop GUI library dependencies.
 */
public class FileLocator {
    private final String filename;
    private File file;
    private File baseFile;
    private File libraryRoot;

    public FileLocator(File file) {
        this(file == null ? null : file.getName());
        this.file = file;
    }

    public FileLocator(String filename) {
        this.filename = filename;
    }

    public FileLocator setBaseFile(File baseFile) {
        this.baseFile = baseFile;
        return this;
    }

    public FileLocator setLibraryRoot(File libraryRoot) {
        if (libraryRoot != null && libraryRoot.isFile()) {
            this.libraryRoot = libraryRoot.getParentFile();
        } else {
            this.libraryRoot = libraryRoot;
        }
        return this;
    }

    public File locate() {
        if (file != null && file.exists()) {
            return file;
        }

        if (filename == null) {
            return file;
        }

        if (baseFile != null) {
            File parent = baseFile.isDirectory() ? baseFile : baseFile.getParentFile();
            if (parent != null) {
                File f = new File(parent, filename);
                if (f.exists()) {
                    return f;
                }
            }
        }

        if (libraryRoot != null) {
            File f = new File(libraryRoot, filename);
            if (f.exists()) {
                return f;
            }
        }

        return file;
    }
}
