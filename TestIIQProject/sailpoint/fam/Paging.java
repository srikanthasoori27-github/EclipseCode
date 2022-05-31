/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.fam;

/**
 * Hold information about the paging for a given datasource
 */
public class Paging {

    public int _start;
    public int _limit;

    public static final int DEFAULT_LIMIT = 100;

    public Paging(int start, int limit) {
        _start = start;
        _limit = limit;
    }

    Paging() { }

    Paging next() {
        if (_limit <= 0) {
            _limit = DEFAULT_LIMIT;
        }
        this._start = _start + _limit;
        return this;
    }

    static Paging firstPage() {
        Paging paging = new Paging();
        paging._start = 0;
        if (paging._limit <= 0) {
            paging._limit = DEFAULT_LIMIT;
        }
        return paging;
    }

}
