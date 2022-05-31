/*
 * (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved.
 */
package sailpoint.service.querybuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.service.listfilter.ListFilterValue.Operation;

public class QueryBuilderService {

    public enum DataTypes {
        String,
        Number,
        Boolean,
        Date,
        Object
    }

    public static Map<DataTypes, List<Operation>> getOperationsByDataType() {

        Map<DataTypes, List<Operation>> result = new HashMap<>();

        // String
        List<Operation> operations = getDefaultOperations();
        operations.add(Operation.StartsWith);
        operations.add(Operation.Contains);
        operations.add(Operation.NotContains);
        result.put(DataTypes.String, operations);

        // Number
        operations = getDefaultOperations();
        operations.add(Operation.GreaterThan);
        operations.add(Operation.GreaterThanOrEqual);
        operations.add(Operation.LessThan);
        operations.add(Operation.LessThanOrEqual);
        result.put(DataTypes.Number, operations);

        // Date
        operations = new ArrayList<>(operations);
        operations.add(Operation.Between);
        operations.add(Operation.TodayOrBefore);
        operations.add(Operation.Before);
        operations.add(Operation.After);
        result.put(DataTypes.Date, operations);

        // Boolean
        result.put(DataTypes.Boolean, getDefaultOperations());

        // Object
        operations = getDefaultOperations();
        operations.add(Operation.StartsWith);
        result.put(DataTypes.Object, operations);

        return result;
    }

    private static List<Operation> getDefaultOperations() {
        List<Operation> operations = new ArrayList<>();
        operations.add(Operation.Equals);
        operations.add(Operation.NotEquals);
        operations.add(Operation.Changed);
        operations.add(Operation.NotChanged);
        operations.add(Operation.ChangedTo);
        operations.add(Operation.ChangedFrom);
        return operations;
    }
}
