/* (c) Copyright 2011 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * Simple layout tool for state diagrams.
 *
 * Author: Jeff
 *
 * This was designed to layout SailPoint workflow steps for display
 * in the GWE but there are no model dependencies and it could be
 * used for other purposes.
 *
 * The state diagram is represented as a list of Node objects.
 * Each Node has a list of "connections" that are other Nodes.
 * Nodes may optionally have a "label" which can factor into the 
 * calculation of the node's width.
 *
 * In the simplest case all node are assumed to have a fixed width and height.
 * If you need to factor in the width of node labels, then an implementation
 * of the NodeSizeCalculator interface must be provided.
 *
 * To use the utility you must first convert the model of interest
 * into a Node list, each Node will have an Object reference back to
 * it's corresponding object in the source model.  Then call the layout()
 * method.  Then iterate over the Nodes copying the resulting x/y coordinates
 * back into the source model.
 *
 */

package sailpoint.tools;

import java.util.ArrayList;
import java.util.List;


public class StateDiagram {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The default font width, used when labels are included in the layout
     * and a NodeSizeCalculator is not supplied.
     */
    public static final int DEFAULT_FONT_WIDTH = 8;
    public static final int DEFAULT_FONT_HEIGHT = 16;

    /**
     * The default width of a node if not specified in the Node object
     * and a NodeSizeCalculator is not supplied.
     */
    public static final int DEFAULT_NODE_WIDTH = 30;
    public static final int DEFAULT_NODE_HEIGHT = 30;

    //////////////////////////////////////////////////////////////////////

    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A list of Nodes to analyize.
     */
    List<Node> _nodes;

    /**
     * True if the layout should be vertical rather than horizontal.
     */
    boolean _vertical;

    /**
     * The default height of a node, may be overridden by the Node
     * or by the NodeSizeCalculator.
     */
    int _nodeWidth;

    /**
     * The default height of a node, may be overridden by the Node
     * or by the NodeSizeCalculator.
     */
    int _nodeHeight;

    /**
     * True if node labels should be factored into the size.
     */
    boolean _includeLabels;

    /**
     * The default width of a label character, can be overridden
     * by NodeSizeCalculator.
     */
    int _fontWidth;

    /**
     * The default beight of a label character, can be overridden
     * by NodeSizeCalculator.
     */
    int _fontHeight;
    
    /**
     * An object that can calculate more accurate node sizes.
     */
    NodeSizeCalculator _nodeSizeCalculator;

    //
    // Transient layout state
    //

    int _rowCount;
    int _columnCount;
    int[] _columnWidths;
    int[] _rowHeights;
    int[] _columnOffsets;
    int[] _rowOffsets;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public StateDiagram() {
    }

    public StateDiagram(List<Node> nodes) {
        _nodes = nodes;
    }

    public void setVertical(boolean b) {
        _vertical = b;
    }

    public void setIncludeLabels(boolean b) {
        _includeLabels = b;
    }

    public void setNodeWidth(int i) {
        _nodeWidth = i;
    }

    public void setNodeHeight(int i) {
        _nodeHeight = i;
    }

    public void setFontWidth(int i) {
        _fontWidth = i;
    }

    public void setFontHeight(int i) {
        _fontHeight = i;
    }

    public void setNodeSizeCalculator(NodeSizeCalculator nsc) {
        _nodeSizeCalculator = nsc;
    }

    public List<Node> getNodes() {
        return _nodes;
    }

    public void setNodes(List<Node> list) {
        _nodes = list;
    }

    public void add(Node other) {
        if (other != null) {
            if (_nodes == null)
                _nodes = new ArrayList<Node>();
            _nodes.add(other);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Look for a previously added node.
     */
    public Node findNode(Object peer) {
        // TODO: HashMap would be faster, assume we won't have
        // to many of these...
        Node found = null;
        if (_nodes != null) {
            for (Node node : _nodes) {
                if (node.getPeer() == peer) {
                    found = node;
                    break;
                }
            }
        }
        return found;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Layout
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Run the layout algorithm over the configured nodes.
     */
    public void layout() {

        _rowCount = 0;
        _columnCount = 0;

        if (_nodes != null && _nodes.size() > 0) {

            initLayout();

            // look for the node designated as root
            Node root = findRoot();

            if (root == null) {
                // throw?
                System.out.println("Unable to layout diagram, no root!");
            }
            else {
                walkHorizontal(root, null);

                resetTraversal();

                walkVertical(root, 1);

                // balanceVertical();

                // We just calculated a horizontal orientation, 
                // flip it if they want vertical
                if (_vertical)
                    rotate();

                calculateSizes();
                assignCoordinates();

                // dumpCoordinates();
            }
        }
    }

    /**
     * Clear all layout state in the nodes.
     */
    public void initLayout() {

        for (Node node : _nodes) {
            node.setTraversed(false);
            node.setRow(0);
            node.setColumn(0);
            node.setLayoutHeight(0);
        }
    }

    /**
     * Clear the traversal flags in all nodes.
     */
    public void resetTraversal() {

        for (Node node : _nodes) {
            node.setTraversed(false);
        }
    }

    /**
     * Locate the root node from which we will start the traversal.
     */
    private Node findRoot() {

        Node root = null;

        // first look for one explicitily designated
        for (Node node : _nodes) {
            if (node.isRoot()) {
                root = node;
                break;
            }
        }

        // then look for the first one that has no inbound connections
        if (root == null) {
            List<Node> roots = findRoots();
            if (roots != null && roots.size() > 1)
                root = roots.get(0);
        }

        // fall back to the first one on the list which for
        // SP workflows will usually be right
        if (root == null)
            root = _nodes.get(0);

        return root;
    }

    /**
     * Locate all potential roots, nodes with no inbound connections.
     */
    private List<Node> findRoots() {

        List<Node> roots = new ArrayList<Node>();
        roots.addAll(_nodes);

        for (Node node : _nodes) {
            List<Node>connections = node.getConnections();
            if (connections != null) {
                for (Node con : connections)
                    roots.remove(con);
            }
        }

        return roots;
    }

    /**
     * Switch between horizontal or vertical orientation.
     */
    public void rotate() {

        // swap rows & columns in each node
        for (Node node : _nodes) {
            int col = node.getColumn();
            node.setColumn(node.getRow());
            node.setRow(col);
        }

        int rc = _rowCount;
        _rowCount = _columnCount;
        _columnCount = rc;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Horizontal Walk
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Walk horizontally from the root assigning columns.
     */
    private void walkHorizontal(Node node, Node prev) {

        // if we're already traversed, ignore
        // this can happen with joins and loops
        node.setTraversed(true);

        // one greater than the previous column
        int col = 1;
        if (prev != null)
            col = prev.getColumn() + 1;

        // compare with current column
        int cur = node.getColumn();

        if (cur == col) {
            // no change, ignore
        }
        else if (cur > col) {
            // someone else already pushed us out farther, ignore
        }
        else {
            // We're being pushed out farther than before, have
            // to adjust
            node.setColumn(col);

            if (col >= _columnCount)
                _columnCount = col + 1;

            // recurse on connections
            List<Node>connections = node.getConnections();
            if (connections != null) {
                for (Node connection : connections) {
                    if (!connection.isTraversed())
                        walkHorizontal(connection, node);
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Vertical Walk
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Vertical walk assigning rows.
     */
    private int walkVertical(Node node, int row) {

        // prevent cycles
        node.setTraversed(true);

        // compare with current column
        int cur = node.getRow();

        if (cur > 0) {
            // we've already traversed through this one and assigned a row
            // ignore
        }
        else {
            node.setRow(row);

            if (row >= _rowCount)
                _rowCount = row + 1;

            int height = 0;

            List<Node> connections = node.getConnections();
            if (connections != null) {
                for (Node connection : connections) {

                    // if it has not yet been assigned a row
                    // it becomes part of our height
                    if (connection.getRow() == 0) {
                        // if next has been traversed, do we still include
                        // its height?
                        if (!connection.isTraversed()) {
                            walkVertical(connection, row + height);
                            height += connection.getLayoutHeight();
                        }
                    }
                }
            }

            // if we have no fanout, height is still one since we occupy space
            if (height == 0)
                height = 1;

            node.setLayoutHeight(height);
        }

        return node.getLayoutHeight();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Balancing
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * An optional step that attempts to reorganizing the basic layout
     * to look more balanced, with fanouts above and below the center line?
     * Haven't used this in awhile so I'm not sure what it does...
     */
    private void balanceVertical() {

        int loops = 0;
        int changes = 1;
        int max = 2;

        while (changes > 0 && loops < max) {

            changes = balanceTransitions();
            if (changes > 0)
                changes = balanceFeeds();

            loops++;
        }

        // System.out.println("Diagram balanced in " + Util.itoa(loops) +
        //" loops.");
    }

    private int balanceTransitions() {

        int loops = 0;
        int changes = 0;
        int innerChanges = 1;

        while (innerChanges > 0) {
            innerChanges = 0;

            for (Node node : _nodes) {

                List<Node> connections = node.getConnections();
                if (connections != null) {

                    int min = -1;
                    int max = 0;

                    for (Node dest : connections) {
                        int row = dest.getRow();
                        if (min < 0 || row < min)
                            min = row;
                        if (row > max)
                            max = row;
                    }

                    int range = max - min + 1;
                    int center = range / 2;
                    int balanced = min + center;
                    int current = node.getRow();

                    // ignore if only one outbound transition (range == 1)

                    if (balanced > current) {
                        node.setRow(balanced);
                        changes++;
                        innerChanges++;

                        if ((range & 1) == 0) {
                            // its even, shift children down
                            for (int j = 0 ; j < connections.size() ; j++) {
                                Node dest = (Node)connections.get(j);
                                if (dest.getRow() >= balanced) {
                                    int newRow = dest.getRow() + 1;
                                    if (newRow >= _rowCount)
                                        _rowCount = newRow + 1;
                                    dest.setRow(newRow);
                                }
                            }
                        }
                    }
                }
            }
            loops++;
        }

        //println("Transitions balanced in " + Util.itoa(loops) + " loops.");

        return changes;
    }

    private int balanceFeeds() {

        int loops = 0;
        int changes = 0;
        int innerChanges = 1;

        while (innerChanges > 0) {
            innerChanges = 0;

            for (Node node : _nodes) {

                List<Node> feeds = getFeeds(node);

                if (feeds != null) {

                    int min = -1;
                    int max = 0;

                    for (Node src : feeds) {
                        int row = src.getRow();
                        if (min < 0 || row < min)
                            min = row;
                        if (row > max)
                            max = row;
                    }

                    int range = max - min + 1;
                    int center = range / 2;
                    int balanced = min + center;
                    int current = node.getRow();

                    // ignore if only one feed

                    if (range > 1 && balanced > current) {
                        node.setRow(balanced);
                        changes++;
                        innerChanges++;

                        if ((range & 1) == 0) {
                            // its even, shift children down
                            for (int j = 0 ; j < feeds.size() ; j++) {
                                Node src = (Node)feeds.get(j);
                                if (src.getRow() >= balanced) {
                                    int newRow = src.getRow() + 1;
                                    if (newRow >= _rowCount)
                                        _rowCount = newRow + 1;
                                    src.setRow(newRow);
                                }
                            }
                        }
                    }
                }
            }
            loops++;
        }

        //println("Feeds balanced in " + Util.itoa(loops) + " loops.");

        return changes;
    }

    private List<Node> getFeeds(Node dest) {

        List<Node> feeds = new ArrayList<Node>();

        for (Node node : _nodes) {
            if (node != dest) {
                List<Node> connections = node.getConnections();
                if (connections != null) {
                    for (Node next : connections) {
                        if (next == dest)
                            feeds.add(node);
                    }
                }
            }
        }

        return feeds;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Size Calculation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Calculate sizes for the node cells.
     */
    private void calculateSizes() {

        _columnWidths = new int[_columnCount];
        _rowHeights = new int[_rowCount];

        for (Node node : _nodes) {

            // might be nice to break this up so it only has to calculate
            // the label dimensions and we do the icon?

            if (_nodeSizeCalculator != null)
                _nodeSizeCalculator.calculateNodeSize(node);
            else
                defaultNodeSizeCalculator(node);

            int width = node.getWidth();
            int height = node.getHeight();

            int cur = _columnWidths[node.getColumn()];
            if (width > cur)
                _columnWidths[node.getColumn()] = width;

            cur = _rowHeights[node.getRow()];
            if (height > cur)
                _rowHeights[node.getRow()] = height;
        }

        calculateOffsets();
    }

    /**
     * Default node size calculator, used when NodeSizeCalculator is
     * not specified.
     *
     * TODO: Nice to support multi-line labels?
     */
    private void defaultNodeSizeCalculator(Node node) {

        // first let the Node builder predefine a size
        int height = node.getHeight();
        if (height <= 0) {
            // then default 
            height = ((_nodeHeight > 0) ? _nodeHeight : DEFAULT_NODE_HEIGHT);

            if (_includeLabels)
                height += ((_fontHeight > 0) ? _fontHeight: DEFAULT_FONT_HEIGHT);

            node.setHeight(height);
        }

        int width = node.getWidth();
        if (width <= 0) {
            width = ((_nodeWidth > 0) ? _nodeWidth : DEFAULT_NODE_WIDTH);

            if (_includeLabels) {
                String label = node.getLabel();
                if (label != null) {
                    int fwidth = ((_fontWidth > 0) ? _fontWidth : DEFAULT_FONT_WIDTH);
                    width += fwidth * label.length();
                }
            }

            node.setWidth(width);
        }
    }

    /**
     * Calculate the offsets to each row and column based on the
     * cell sizes.
     */
    private void calculateOffsets() {

        _columnOffsets = new int[_columnWidths.length + 1];
        _rowOffsets = new int[_rowHeights.length + 1];

        int horizPad = 0;
        int vertPad = 0;

        // give the diagram a left pad to adjust for centering
        // of node text
        int last = 20;

        int i = 0;
        for ( ; i < _columnWidths.length ; i++) {
            _columnOffsets[i] = last;
            last += _columnWidths[i] + horizPad;
        }
        _columnOffsets[i] = last;
        
        // give the diagram a top pad to give it some breathing room
        last = 10;

        i = 0;
        for ( ; i < _rowHeights.length ; i++) {
            _rowOffsets[i] = last;
            last += _rowHeights[i] + vertPad;
        }
        _rowOffsets[i] = last;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Coordinate Assignment
    //
    //////////////////////////////////////////////////////////////////////

    private void assignCoordinates() {
        
        // give them a little padding
        int gridRowOffset = 0;
        int gridColOffset = ((_fontWidth > 0) ? _fontWidth : DEFAULT_FONT_WIDTH);
        for (Node node : _nodes) {

            int x = gridColOffset + _columnOffsets[node.getColumn()];
            int y = gridRowOffset + _rowOffsets[node.getRow()];

            // center vertical

            int height = node.getHeight();
            int rowheight = _rowHeights[node.getRow()];
            int delta = rowheight - height;
            y += delta / 2;

            // center horizontal
            int width = node.getWidth();
            int colwidth = _columnWidths[node.getColumn()];
            delta = colwidth - width;
            x += delta / 2;

            node.setX(x);
            node.setY(y);
        }
    }

    /**
     * Dump the layout coordinates for debugging.
     */
    private void dumpCoordinates() {

        if (_columnWidths != null) {
            println("Column widths:");
            for (int i = 0 ; i < _columnWidths.length ; i++)
                println("  " + Util.itoa(_columnWidths[i]));
        }

        if (_columnOffsets != null) {
            println("Column offsets:");
            for (int i = 0 ; i < _columnOffsets.length ; i++)
                println("  " + Util.itoa(_columnOffsets[i]));
        }

        if (_rowHeights != null) {
            println("Row heights:");
            for (int i = 0 ; i < _rowHeights.length ; i++)
                println("  " + Util.itoa(_rowHeights[i]));
        }

        if (_rowOffsets != null) {
            println("Row offsets:");
            for (int i = 0 ; i < _rowOffsets.length ; i++)
                println("  " + Util.itoa(_rowOffsets[i]));
        }

    }

    public void println(String msg) {
        System.out.println(msg);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Node
    //
    //////////////////////////////////////////////////////////////////////

    public static class Node {

        //
        // Node properties from the model
        //

        /**
         * The object that this node was built from.
         */
        Object _peer;

        /**
         * True if this is the root node.  If not specified the
         * first Node in the diagram is considered the root.
         */
        boolean _root;

        /**
         * Optional node label if we need to factor in text size.
         */
        String _label;

        /**
         * Pre-calculated node width, if not specifiefd then a
         * default width or NodeSizeCalculator is used.
         */
        int _width;

        /**
         * Pre-calculated node height, if not specifiefd then a
         * default height or NodeSizeCalculator is used.
         */
        int _height;

        /**
         * List of outbound connections to other nodes.
         */
        List<Node> _connections;
       
        //
        // State calculated during layout
        //

        /**
         * True if we have traversed this node during layout.
         */
        boolean _traversed;

        /**
         * Current row in the layout.
         */
        int _row;

        /**
         * Current column in the layout.
         */
        int _column;

        /**
         * The layout height including connections.
         */
        int _layoutHeight;

        /**
         * The final calculated X coordinate.
         */
        int _x;

        /**
         * The final calculated Y coordinate.
         */
        int _y;

        public Node(Object peer) {
            _peer = peer;
        }

        public Object getPeer() {
            return _peer;
        }

        public boolean isRoot() {
            return _root;
        }

        public void setRoot(boolean b) {
            _root = b;
        }

        public String getLabel() {
            return _label;
        }

        public void setLabel(String s) {
            _label = s;
        }

        public int getWidth() {
            return _width;
        }

        public void setWidth(int i) {
            _width = i;
        }

        public int getHeight() {
            return _height;
        }

        public void setHeight(int i) {
            _height = i;
        }

        public List<Node> getConnections() {
            return _connections;
        }

        public void setConnections(List<Node> list) {
            _connections = list;
        }

        public void add(Node other) {
            if (other != null) {
                if (_connections == null)
                    _connections = new ArrayList<Node>();
                _connections.add(other);
            }
        }

        public boolean isTraversed() {
            return _traversed;
        }

        public void setTraversed(boolean b) {
            _traversed = b;
        }

        public int getColumn() {
            return _column;
        }

        public void setColumn(int c) {
            _column = c;
        }

        public int getRow() {
            return _row;
        }

        public void setRow(int r) {
            _row = r;
        }

        public int getLayoutHeight() {
            return _layoutHeight;
        }

        public void setLayoutHeight(int h) {
            _layoutHeight = h;
        }

        public int getX() {
            return _x;
        }

        public void setX(int x) {
            _x = x;
        }

        public int getY() {
            return _y;
        }

        public void setY(int y) {
            _y = y;
        }

    }

    //////////////////////////////////////////////////////////////////////
    //
    // Size Calculators
    //
    //////////////////////////////////////////////////////////////////////

    public interface NodeSizeCalculator {

        void calculateNodeSize(Node node);

    }


}

