/*
 * Copyright (c) 2016 Helmut Neemann
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.draw.elements;

import de.neemann.digital.core.ExceptionWithOrigin;
import de.neemann.digital.draw.model.Net;

/**
 * Exception thrown dealing with pins (headless compatible).
 */
public class PinException extends ExceptionWithOrigin {
    private Net net;

    public PinException(String message, Object visualElement) {
        super(message);
        setVisualElement(visualElement);
    }

    public PinException(String message, Net net) {
        super(message);
        this.net = net;
        if (net != null) {
            setOrigin(net.getOrigin());
        }
    }

    public PinException(String message) {
        super(message);
    }

    public Net getNet() {
        return net;
    }
}
