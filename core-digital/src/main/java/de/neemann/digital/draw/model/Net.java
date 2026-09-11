/*
 * Copyright (c) 2016 Helmut Neemann
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.draw.model;

import java.io.File;

/**
 * Headless Net model holder.
 */
public class Net {
    private File origin;
    private Object visualElement;

    public Net() {
    }

    public Net(File origin) {
        this.origin = origin;
    }

    public File getOrigin() {
        return origin;
    }

    public void setOrigin(File origin) {
        this.origin = origin;
    }

    public Object getVisualElement() {
        return visualElement;
    }

    public void setVisualElement(Object visualElement) {
        this.visualElement = visualElement;
    }
}
