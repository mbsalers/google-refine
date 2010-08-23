package com.google.gridworks.protograph.transpose;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.gridworks.model.Cell;
import com.google.gridworks.model.Column;
import com.google.gridworks.model.Project;
import com.google.gridworks.model.Recon;
import com.google.gridworks.model.Recon.Judgment;
import com.google.gridworks.model.recon.ReconConfig;
import com.google.gridworks.model.recon.StandardReconConfig;
import com.google.gridworks.protograph.AnonymousNode;
import com.google.gridworks.protograph.CellKeyNode;
import com.google.gridworks.protograph.CellNode;
import com.google.gridworks.protograph.CellTopicNode;
import com.google.gridworks.protograph.CellValueNode;
import com.google.gridworks.protograph.FreebaseProperty;
import com.google.gridworks.protograph.FreebaseTopic;
import com.google.gridworks.protograph.FreebaseTopicNode;
import com.google.gridworks.protograph.Link;
import com.google.gridworks.protograph.ValueNode;

public class TripleLoaderTransposedNodeFactory implements TransposedNodeFactory {
    protected Project project;
    
    protected boolean start = true;
    protected Writer writer;
    protected WritingTransposedNode lastRootNode;
    protected Map<String, Long> varPool = new HashMap<String, Long>();
    protected Map<Long, String> newTopicVars = new HashMap<Long, String>();
    protected Set<Long> serializedRecons = new HashSet<Long>();
    
    protected long contextID = 0;
    protected int contextRowIndex;
    protected int contextRefCount = 0;
    protected JSONObject contextTreeRoot;
    
    public TripleLoaderTransposedNodeFactory(Project project, Writer writer) {
        this.project = project;
        this.writer = writer;
    }
    
    @Override
    public void flush() throws IOException {
        if (lastRootNode != null) {
            lastRootNode.write(null, null, project, -1, -1, null);
            lastRootNode = null;
            
            writeContextTreeNode();
        }
    }
    
    protected void writeLine(String line) {
        try {
            if (start) {
                start = false;
            } else {
                writer.write('\n');
            }
            writer.write(line);
        } catch (IOException e) {
            // ignore
        }
    }
    
    protected void writeRecon(
        StringBuffer sb, 
        Project project, 
        int rowIndex, 
        int cellIndex, 
        Cell cell
    ) {
        Recon recon = cell.recon;
        
        sb.append("\"rec"); sb.append(Long.toString(recon.id)); sb.append("\"");
        contextRefCount++;
        
        if (!serializedRecons.contains(recon.id)) {
            serializedRecons.add(recon.id);
            
            Column column = project.columnModel.getColumnByCellIndex(cellIndex);
            
            // qa:sample_group
            {
                StringBuffer sb2 = new StringBuffer();
                
                sb2.append("{ \"s\" : \"rec"); 
                sb2.append(Long.toString(recon.id)); 
                sb2.append("\", \"p\" : \"qa:sample_group\", \"o\" : ");
                sb2.append(JSONObject.quote(column.getName()));
                sb2.append(", \"ignore\" : true }");
                
                writeLine(sb2.toString());
            }
            
            // qa:recon_data
            {
                StringBuffer sb2 = new StringBuffer();
                
                String s = cell.value instanceof String ? (String) cell.value : cell.value.toString();
                    
                sb2.append("{ \"s\" : \"rec"); 
                sb2.append(Long.toString(recon.id)); 
                sb2.append("\", \"p\" : \"qa:recon_data\", \"ignore\" : true, \"o\" : { ");
                
                sb2.append(" \"history_entry\" : "); sb2.append(Long.toString(recon.judgmentHistoryEntry));
                sb2.append(", \"text\" : "); sb2.append(JSONObject.quote(s));
                sb2.append(", \"column\" : "); sb2.append(JSONObject.quote(column.getName()));
                sb2.append(", \"service\" : "); sb2.append(JSONObject.quote(recon.service));
                sb2.append(", \"action\" : "); sb2.append(JSONObject.quote(recon.judgmentAction));
                sb2.append(", \"batch\" : "); sb2.append(Integer.toString(recon.judgmentBatchSize));
                
                if (recon.judgment == Judgment.Matched) {
                    sb2.append(", \"matchRank\" : "); sb2.append(Integer.toString(recon.matchRank));
                    sb2.append(", \"id\" : "); sb2.append(JSONObject.quote(recon.match.id));
                }
                
                ReconConfig reconConfig = column.getReconConfig();
                if (reconConfig != null && reconConfig instanceof StandardReconConfig) {
                    StandardReconConfig standardReconConfig = (StandardReconConfig) reconConfig;
                    sb2.append(", \"type\" : "); sb2.append(JSONObject.quote(standardReconConfig.typeID));
                }
                
                sb2.append(" } }");
                
                writeLine(sb2.toString());
            }
        }
    }
    
    protected void writeLine(
            String subject, String predicate, Object object, 
            Project project, 
            int subjectRowIndex, int subjectCellIndex, Cell subjectCell, 
            int objectRowIndex, int objectCellIndex, Cell objectCell,
            boolean ignore
        ) {
        if (subject != null && object != null) {
            String s = object instanceof String ? 
                    JSONObject.quote((String) object) : object.toString();
                    
            StringBuffer sb = new StringBuffer();
            sb.append("{ \"s\" : \""); sb.append(subject); sb.append('"');
            sb.append(", \"p\" : \""); sb.append(predicate); sb.append('"');
            sb.append(", \"o\" : "); sb.append(s);
            if (subjectCell != null || objectCell != null) {
                sb.append(", \"meta\" : { ");
                
                sb.append("\"recon\" : { ");
                if (subjectCell != null) {
                    sb.append("\"s\" : ");
                    writeRecon(sb, project, subjectRowIndex, subjectCellIndex, subjectCell);
                }
                if (objectCell != null) {
                    if (subjectCell != null) {
                        sb.append(", ");
                    }
                    sb.append("\"o\" : ");
                    writeRecon(sb, project, objectRowIndex, objectCellIndex, objectCell);
                }
                sb.append(" }");
                
                sb.append(" }");
            }
            if (ignore) {
                sb.append(", \"ignore\" : true");
            }
            sb.append(" }");
                    
            writeLine(sb.toString());
        }
    }
    
    protected void writeLine(
        String subject, String predicate, Object object, String lang, 
        Project project, int subjectRowIndex, int subjectCellIndex, Cell subjectCell,
        boolean ignore
    ) {
        if (subject != null && object != null) {
            String s = object instanceof String ? 
                    JSONObject.quote((String) object) : object.toString();
                    
            StringBuffer sb = new StringBuffer();
            sb.append("{ \"s\" : \""); sb.append(subject); sb.append('"');
            sb.append(", \"p\" : \""); sb.append(predicate); sb.append('"');
            sb.append(", \"o\" : "); sb.append(s);
            sb.append(", \"lang\" : "); sb.append(lang);
                    
            if (subjectCell != null) {
                sb.append(", \"meta\" : { ");
                sb.append("\"recon\" : { ");
                sb.append("\"s\" : ");
                writeRecon(sb, project, subjectRowIndex, subjectCellIndex, subjectCell);
                sb.append(" }");
                sb.append(" }");
            }
            if (ignore) {
                sb.append(", \"ignore\" : true");
            }
            sb.append(" }");
                    
            writeLine(sb.toString());
        }
    }
    
    abstract protected class WritingTransposedNode implements TransposedNode {
        JSONObject jsonContextNode;
        boolean load;
        
        public Object write(
                String subject, String predicate, Project project,
                int subjectRowIndex, int subjectCellIndex, Cell subjectCell) {
            
            return internalWrite(
                subject, predicate, project, 
                subjectRowIndex, subjectCellIndex, subjectCell);
        }
        
        abstract public Object internalWrite(
            String subject, String predicate, Project project,
            int subjectRowIndex, int subjectCellIndex, Cell subjectCell);
    }
    
    abstract protected class TransposedNodeWithChildren extends WritingTransposedNode {
        public List<Link> links = new LinkedList<Link>();
        public List<Integer> rowIndices = new LinkedList<Integer>();
        public List<WritingTransposedNode> children = new LinkedList<WritingTransposedNode>();
        
        protected void writeChildren(
            String subject, Project project,
            int subjectRowIndex, int subjectCellIndex, Cell subjectCell) {
            
            for (int i = 0; i < children.size(); i++) {
                WritingTransposedNode child = children.get(i);
                Link link = links.get(i);
                String predicate = link.property.id;
                
                child.write(subject, predicate, project, 
                    subjectRowIndex, subjectCellIndex, subjectCell);
            }
        }
    }
    
    protected class AnonymousTransposedNode extends TransposedNodeWithChildren {
        
        //protected AnonymousTransposedNode(AnonymousNode node) { }
        
        public Object internalWrite(String subject, String predicate, Project project, int subjectRowIndex, int subjectCellIndex, Cell subjectCell) {
            if (children.size() == 0 || subject == null) {
                return null;
            }
            
            StringBuffer sb = new StringBuffer();
            sb.append("{ \"s\" : \""); sb.append(subject); sb.append('"');
            sb.append(", \"p\" : \""); sb.append(predicate); sb.append('"');
            sb.append(", \"o\" : { ");
            
            StringBuffer sbRecon = new StringBuffer();
            
            boolean first = true;
            boolean firstRecon = true;
            
            if (subjectCell.recon != null) {
                sbRecon.append("\"s\" : ");
                writeRecon(sbRecon, project, subjectRowIndex, subjectCellIndex, subjectCell);
                
                firstRecon = false;
            }
            
            for (int i = 0; i < children.size(); i++) {
                WritingTransposedNode child = children.get(i);
                Link link = links.get(i);
                
                FreebaseProperty property = link.property;
                
                Object c = child.internalWrite(null, null, project, subjectRowIndex, subjectCellIndex, null);
                if (c != null) {
                    if (first) {
                        first = false;
                    } else {
                        sb.append(", ");
                    }
                    sb.append("\"" + property.id + "\": ");
                    sb.append(c instanceof String ? JSONObject.quote((String) c) : c.toString());
                }
                
                if (child instanceof CellTopicTransposedNode) {
                    CellTopicTransposedNode child2 = (CellTopicTransposedNode) child;
                    Recon recon = child2.cell.recon;
                    
                    if (recon != null &&
                        (recon.judgment == Judgment.Matched || recon.judgment == Judgment.New)) {
                        
                        if (firstRecon) {
                            firstRecon = false;
                        } else {
                            sbRecon.append(", ");
                        }
                        
                        sbRecon.append("\""); sbRecon.append(property.id); sbRecon.append("\" : ");
                        
                        writeRecon(sbRecon, project, 
                            rowIndices.get(i), child2.cellIndex, child2.cell);
                    }
                }
            }
            sb.append(" }, \"meta\" : { \"recon\" : { ");
            sb.append(sbRecon.toString());
            sb.append(" } } }");
            
            writeLine(sb.toString());
            
            return null;
        }
    }
    
    protected class CellTopicTransposedNode extends TransposedNodeWithChildren {
        protected CellTopicNode node;
        protected int rowIndex;
        protected int cellIndex;
        protected Cell cell;
        
        public CellTopicTransposedNode(CellTopicNode node, int rowIndex, int cellIndex, Cell cell) {
            this.node = node;
            this.rowIndex = rowIndex;
            this.cellIndex = cellIndex;
            this.cell = cell;
        }
        
        public Object internalWrite(String subject, String predicate, Project project, int subjectRowIndex, int subjectCellIndex, Cell subjectCell) {
            String id = null;
            if (cell.recon != null && cell.recon.judgment != Recon.Judgment.None) {
                int objectRowIndex = rowIndex;
                int objectCellIndex = cellIndex;
                Cell objectCell = cell;
                
                if (cell.recon.judgment == Recon.Judgment.Matched) {
                    id = cell.recon.match.id;
                    
                } else if (cell.recon.judgment == Judgment.New) {
                    if (newTopicVars.containsKey(cell.recon.id)) {
                        id = newTopicVars.get(cell.recon.id);
                    } else {
                        Column column = project.columnModel.getColumnByCellIndex(cellIndex);
                        String columnName = column.getName();
                        
                        long var = 0;
                        if (varPool.containsKey(columnName)) {
                            var = varPool.get(columnName);
                        }
                        varPool.put(columnName, var + 1);
                        
                        id = "$" + columnName.replaceAll("\\W+", "_") + "_" + var;
                        
                        String typeID = node.type.id;
                        
                        ReconConfig reconConfig = column.getReconConfig();
                        if (reconConfig instanceof StandardReconConfig) {
                            typeID = ((StandardReconConfig) reconConfig).typeID;
                        }
                        
                        writeLine(id, "type", typeID, project, rowIndex, cellIndex, cell, -1, -1, (Cell) null, !load);
                        writeLine(id, "name", cell.value, project, -1, -1, (Cell) null, -1, -1, (Cell) null, !load);
                        
                        if (cell.recon != null) {
                            newTopicVars.put(cell.recon.id, id);
                        }
                    }
                } else {
                    return null;
                }
                
                if (subject != null) {
                    writeLine(subject, predicate, id, project, 
                            subjectRowIndex, subjectCellIndex, subjectCell, 
                            objectRowIndex, objectCellIndex, objectCell, !load);
                }
                
                writeChildren(id, project, objectRowIndex, objectCellIndex, objectCell);
            }
            
            return id;
        }
    }
    
    protected class CellValueTransposedNode extends WritingTransposedNode {
        protected JSONObject obj;
        protected CellValueNode node;
        protected int rowIndex;
        protected int cellIndex;
        protected Cell cell;
        
        public CellValueTransposedNode(CellValueNode node, int rowIndex, int cellIndex, Cell cell) {
            this.node = node;
            this.rowIndex = rowIndex;
            this.cellIndex = cellIndex;
            this.cell = cell;
        }
        
        public Object internalWrite(String subject, String predicate, Project project, int subjectRowIndex, int subjectCellIndex, Cell subjectCell) {
            if (subject != null) {
                if ("/type/text".equals(node.lang)) {
                    writeLine(subject, predicate, cell.value, node.lang, project, 
                            subjectRowIndex, subjectCellIndex, subjectCell, !load);
                } else {
                    writeLine(subject, predicate, cell.value, project, 
                            subjectRowIndex, subjectCellIndex, subjectCell, 
                            -1, -1, null, !load);
                }
            }
            
            return cell.value;
        }
    }
    
    protected class CellKeyTransposedNode extends WritingTransposedNode {
        protected CellKeyNode node;
        protected int rowIndex;
        protected int cellIndex;
        protected Cell cell;
        
        public CellKeyTransposedNode(CellKeyNode node, int rowIndex, int cellIndex, Cell cell) {
            this.node = node;
            this.rowIndex = rowIndex;
            this.cellIndex = cellIndex;
            this.cell = cell;
        }
        
        public Object internalWrite(String subject, String predicate, Project project, int subjectRowIndex, int subjectCellIndex, Cell subjectCell) {
            writeLine(subject, "key", node.namespace.id + "/" + cell.value, project, 
                subjectRowIndex, subjectCellIndex, subjectCell, 
                -1, -1, null, !load);
            
            return null;
        }
    }
    
    protected class TopicTransposedNode extends TransposedNodeWithChildren {
        protected FreebaseTopicNode node;
        
        public TopicTransposedNode(FreebaseTopicNode node) {
            this.node = node;
        }

        public Object internalWrite(String subject, String predicate, Project project, int subjectRowIndex, int subjectCellIndex, Cell subjectCell) {
            writeLine(subject, predicate, node.topic.id, project, 
                subjectRowIndex, subjectCellIndex, subjectCell, 
                -1, -1, null, !load);
            
            writeChildren(node.topic.id, project, -1, -1, null);
            
            return node.topic.id;
        }
    }

    protected class ValueTransposedNode extends WritingTransposedNode {
        protected ValueNode node;
        
        public ValueTransposedNode(ValueNode node) {
            this.node = node;
        }

        public Object internalWrite(String subject, String predicate, Project project, int subjectRowIndex, int subjectCellIndex, Cell subjectCell) {
            if ("/type/text".equals(node.lang)) {
                writeLine(subject, predicate, node.value, node.lang, project,
                    subjectRowIndex, subjectCellIndex, subjectCell, !load);
            } else {
                writeLine(subject, predicate, node.value, project, 
                    subjectRowIndex, subjectCellIndex, subjectCell, 
                    -1, -1, null, !load);
            }
            
            return node.value;
        }
    }
    
    public TransposedNode transposeAnonymousNode(
            TransposedNode parentNode,
            Link link, 
            AnonymousNode node, int rowIndex) {
        
        WritingTransposedNode parentNode2 = (WritingTransposedNode) parentNode;
        WritingTransposedNode tnode = new AnonymousTransposedNode();
        
        tnode.load = 
            (parentNode2 == null || parentNode2.load) &&
            (link == null || link.load);
        
        processTransposedNode(tnode, parentNode, link, rowIndex);
        
        tnode.jsonContextNode = addJsonContext(
            parentNode2 != null ? parentNode2.jsonContextNode : null,
            link != null ? link.property.id : null,
            null
        );
        
        return tnode;
    }

    public TransposedNode transposeCellNode(
            TransposedNode parentNode,
            Link link, 
            CellNode node, 
            int rowIndex,
            int cellIndex,
            Cell cell) {
        
        WritingTransposedNode parentNode2 = (WritingTransposedNode) parentNode;
        
        WritingTransposedNode tnode = null;
        if (node instanceof CellTopicNode) {
            if (cell.recon != null && 
                    (cell.recon.judgment == Judgment.Matched ||
                            cell.recon.judgment == Judgment.New)) {
                
                tnode = new CellTopicTransposedNode(
                    (CellTopicNode) node, rowIndex, cellIndex, cell);
            }
        } else if (node instanceof CellValueNode) {
            tnode = new CellValueTransposedNode((CellValueNode) node, rowIndex, cellIndex, cell);
        } else if (node instanceof CellKeyNode) {
            tnode = new CellKeyTransposedNode((CellKeyNode) node, rowIndex, cellIndex, cell);
        }
        
        if (tnode != null) {
            tnode.load = 
                (parentNode2 == null || parentNode2.load) &&
                (link == null || link.load);
            
            processTransposedNode(tnode, parentNode, link, rowIndex);
            
            tnode.jsonContextNode = addJsonContext(
                parentNode2 != null ? parentNode2.jsonContextNode : null,
                link != null ? link.property.id : null,
                cell,
                rowIndex
            );
        }
        return tnode;
    }

    public TransposedNode transposeTopicNode(
            TransposedNode parentNode,
            Link link, 
            FreebaseTopicNode node,
            int rowIndex) {
        
        WritingTransposedNode parentNode2 = (WritingTransposedNode) parentNode;
        WritingTransposedNode tnode = new TopicTransposedNode(node);
        
        tnode.load = 
            (parentNode2 == null || parentNode2.load) &&
            (link == null || link.load);
        
        processTransposedNode(tnode, parentNode, link, rowIndex);
        
        tnode.jsonContextNode = addJsonContext(
            parentNode2 != null ? parentNode2.jsonContextNode : null,
            link != null ? link.property.id : null,
            node.topic
        );
        
        return tnode;
    }

    public TransposedNode transposeValueNode(
            TransposedNode parentNode,
            Link link, 
            ValueNode node,
            int rowIndex) {
        
        WritingTransposedNode parentNode2 = (WritingTransposedNode) parentNode;
        WritingTransposedNode tnode = new ValueTransposedNode(node);
        
        tnode.load = 
            (parentNode2 == null || parentNode2.load) &&
            (link == null || link.load);
        
        processTransposedNode(tnode, parentNode, link, rowIndex);
        
        tnode.jsonContextNode = addJsonContext(
            parentNode2 != null ? parentNode2.jsonContextNode : null,
            link != null ? link.property.id : null,
            node.value
        );
        
        return tnode;
    }
    
    protected void processTransposedNode(
        WritingTransposedNode  tnode, 
        TransposedNode         parentNode,
        Link                   link,
        int                    rowIndex 
    ) {
        if (parentNode != null) {
            if (parentNode instanceof TransposedNodeWithChildren) {
                TransposedNodeWithChildren parentNode2 = (TransposedNodeWithChildren) parentNode;
                parentNode2.rowIndices.add(rowIndex);
                parentNode2.children.add(tnode);
                parentNode2.links.add(link);
            }
        } else {
            addRootNode(tnode, rowIndex);
        }
    }
    
    protected JSONObject addJsonContext(JSONObject parent, String key, Object value) {
        JSONObject o = new JSONObject();
        
        try {
            if (value instanceof FreebaseTopic) {
                FreebaseTopic topic = (FreebaseTopic) value;
                o.put("id", topic.id);
                o.put("name", topic.name);
            } else {
                o.put("v", value);
            }
        } catch (JSONException e) {
            // ignore
        }
        
        connectJsonContext(parent, o, key);
        return o;
    }
    
    protected JSONObject addJsonContext(JSONObject parent, String key, Cell cell, int rowIndex) {
        JSONObject o = new JSONObject();
        
        connectJsonContext(parent, o, key);
        
        try {
            if (cell != null) {
                o.put("v", cell.value);
                if (cell.recon != null) {
                    o.put("recon", "rec" + cell.recon.id);
                    
                    if (cell.recon.judgment == Judgment.Matched) {
                        o.put("id", cell.recon.match.id);
                        o.put("name", cell.recon.match.name);
                    }
                    
                    // qa:display_context
                    {
                        StringBuffer sb2 = new StringBuffer();
                        
                        sb2.append("{ \"ignore\" : true, \"s\" : \"rec");
                        sb2.append(Long.toString(cell.recon.id));
                        sb2.append("\", \"p\" : \"qa:display_context\", \"o\" : \"ctx");
                        sb2.append(Long.toString(contextID));
                        sb2.append("\", \"meta\" : { \"row\" : ");
                        sb2.append(Integer.toString(rowIndex));
                        sb2.append(" } }");
                        
                        writeLine(sb2.toString());
                    }
                }
            }
        } catch (JSONException e) {
            // ignore
        }
        
        return o;
    }
    
    protected void connectJsonContext(JSONObject parent, JSONObject o, String key) {
        try {
            if (parent == null) {
                contextTreeRoot = o;
            } else {
                JSONArray a = null;
                if (parent.has(key)) {
                    a = parent.getJSONArray(key);
                } else {
                    a = new JSONArray();
                    parent.put(key, a);
                }
                
                a.put(o);
            }
        } catch (JSONException e) {
            // ignore
        }
    }
    
    protected void addRootNode(WritingTransposedNode tnode, int rowIndex) {
        if (lastRootNode != null) {
            lastRootNode.write(null, null, project, -1, -1, null);
            writeContextTreeNode();
        }
        lastRootNode = tnode;
        
        contextTreeRoot = null;
        contextRowIndex = rowIndex;
        contextRefCount = 0;
        contextID++;
    }
    
    protected void writeContextTreeNode() {
        if (contextTreeRoot != null && contextRefCount > 0) {
            StringBuffer sb = new StringBuffer();
            
            sb.append("{ \"ignore\" : true, \"s\" : \"ctx"); 
            sb.append(Long.toString(contextID)); 
            sb.append("\", \"p\" : \"qa:context_data\", \"o\" : { \"row\" : ");
            sb.append(Integer.toString(contextRowIndex));
            sb.append(", \"data\" : ");
            sb.append(contextTreeRoot.toString());
            sb.append(" } }");
            
            writeLine(sb.toString());
        }
    }
}