/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.fam;

import java.util.Iterator;
import java.util.function.Function;

public class PagedIterator<T> implements Iterator<T> {

    private final Function<Paging, Iterator<T>>  _iteratorProvider;
    Paging _currentPage;
    private Iterator<T> _currentIterator;

    public PagedIterator(Paging initialPage, Function<Paging, Iterator<T>> iteratorProvider) {
        _currentPage = initialPage;
        this._iteratorProvider = iteratorProvider;
        _currentIterator = _iteratorProvider.apply(_currentPage);
    }

    @Override
    public boolean hasNext() {
        if (_currentIterator.hasNext()) {
            return true;
        } else {
            //Increment Page and try again
            _currentPage = _currentPage.next();
            _currentIterator = _iteratorProvider.apply(_currentPage);
            return _currentIterator.hasNext();
        }
    }

    @Override
    public T next() {
        return _currentIterator.next();
    }
}
