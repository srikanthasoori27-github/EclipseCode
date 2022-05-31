package sailpoint.rest;

/**
 * @author: peter.holcomb
 *
 * DTO for holding type and collection of items for certain types of Bad Request responses.
 */
public class BadRequestDTO {
    private String type;
    private Object items;

    /**
     * Constructs the DTO.
     * @param type the type
     * @param items the items; in practice this should be a collection or map
     */
    public BadRequestDTO(String type, Object items) {
        this.type = type;
        this.items = items;
    }

    public String getType() {
        return type;
    }

    public Object getItems() {
        return items;
    }
}
