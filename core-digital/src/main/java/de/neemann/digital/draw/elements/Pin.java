/*
 * Copyright (c) 2016 Helmut Neemann
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.draw.elements;

import de.neemann.digital.core.ObservableValue;

/**
 * Headless Pin model holder.
 */
public class Pin {
    private final ObservableValue value;

    public Pin(ObservableValue value) {
        this.value = value;
    }

    public ObservableValue getValue() {
        return value;
    }
}
