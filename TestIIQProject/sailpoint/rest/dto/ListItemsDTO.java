/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rest.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The DTO to transfer a collection of items with identifiers and display names (and sorted by display name).
 *
 * @param <T> type of the identifier
 */
public class ListItemsDTO<T> {
    private final List<ListItemDTO<T>> items = new ArrayList<>();

    public ListItemsDTO<T> addItem(ListItemDTO<T> item) {
        if (item != null) {
            items.add(item);
        }

        return this;
    }

    public List<ListItemDTO<T>> getItems() {
        return Collections.unmodifiableList(items);
    }

    public ListItemsDTO<T> sortItemsByName() {
        items.sort(Comparator.comparing(ListItemDTO::getDisplayName));

        return this;
    }

    public static class ListItemDTO<T> {
        private final T id;
        private final String displayName;

        public ListItemDTO(T id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public T getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
