/*
 * Copyright (c) 2018 Helmut Neemann.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.hdl.hgs;

/**
 * Headless HGSMap interface.
 */
public interface HGSMap {
    Object hgsMapGet(String key) throws HGSEvalException;
}
