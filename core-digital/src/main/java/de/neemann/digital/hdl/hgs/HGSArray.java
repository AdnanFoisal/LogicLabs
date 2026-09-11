/*
 * Copyright (c) 2018 Helmut Neemann.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.hdl.hgs;

/**
 * Headless HGSArray interface.
 */
public interface HGSArray {
    int hgsArraySize() throws HGSEvalException;
    default void hgsArrayAdd(Object initial) throws HGSEvalException {
        throw new HGSEvalException("Array growth is not supported!");
    }
    default void hgsArraySet(int i, Object val) throws HGSEvalException {
        throw new HGSEvalException("Setting value not allowed!");
    }
    Object hgsArrayGet(int i) throws HGSEvalException;
}
