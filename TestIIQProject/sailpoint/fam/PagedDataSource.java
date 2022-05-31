/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.fam;

import java.util.List;

public interface PagedDataSource<T> {

    List<T> getEntities(Paging paging);

}
