package sailpoint.request;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.Terminator;
import sailpoint.object.Attributes;
import sailpoint.object.Request;
import sailpoint.object.SailPointObject;
import sailpoint.object.TaskResult;
import sailpoint.task.PrunerTask;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.Compressor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class PrunerRequestExecutor extends AbstractRequestExecutor {
    
    private static final Log log = LogFactory.getLog(PrunerRequestExecutor.class);

    private static final int DECACHE_EVERY = 10;
    
    private Class<? extends SailPointObject> _clazz;
    private List<String> _objIdList;
    private TaskResult _result;
    private SailPointContext _context;
    private Map<String, Integer> _deletedObjects;

    private boolean _terminated;

    @Override
    public void execute(SailPointContext context, Request request, Attributes<String, Object> args)
            throws RequestPermanentException, RequestTemporaryException {

        _context = context;
        _deletedObjects = new HashMap<String, Integer>();
        TaskMonitor mon = new TaskMonitor(_context, request);
        int deleted = 0;
        try {
            _result = mon.lockPartitionResult();
            // Object-ID list is a compressed CSV string
            // in the form of Class::ID
            String compressedList = args.getString(PrunerTask.ARG_ID_LIST);
            if (!Util.isEmpty(compressedList)) {
                _objIdList = Util.csvToList(Compressor.decompress(compressedList));
                if (log.isDebugEnabled()) {
                    StringBuilder buff = new StringBuilder();
                    buff.append("id list: [");
                    for (Object id : _objIdList) {
                        buff.append(id).append(", ");
                    }
                    buff.append("]");
                    log.debug(buff.toString());
                }
                deleted = deleteObjects();
            } else {
                _result.addMessage(Message.error(MessageKeys.ERR_NO_OBJECTS));
            }
            
            _result.addAttribute(PrunerTask.RET_OBJS_DELETED, deleted);
            _result.getAttributes().putAll(_deletedObjects);

        } catch (GeneralException ge) {
            // capture the error in the taskresult
            if (_result != null) {
                _result.addMessage(new Message(Message.Type.Error, ge));
            }
            // and in log4j
            log.error(ge.getMessage(), ge);
        } finally {
            try {
                mon.commitPartitionResult();
            } catch (GeneralException ge) {
                log.error(ge.getMessage(), ge);
            }
        }
    }
    
    private int deleteObjects() throws GeneralException {
        Terminator arnold = new Terminator(_context);
        int counter = 0;
        String classMapName = null;
        int classCount = 0;
        for (String objId : _objIdList) {
            if (isTerminated()) {
                return counter;
            }
            String[] tokens = objId.split(PrunerTask.CLASS_ID_DELIM, 2);
            if (tokens == null || tokens.length < 2) {
                _result.addMessage(Message.error("Cannot parse object-id string: ", objId));
            }
            String className = tokens[0];
            String id = tokens[1];
            if (Util.isNullOrEmpty(className)) {
                _result.addMessage(Message.error(MessageKeys.ERR_NO_CLASS_FAILED));
            } else {
                try {
                    // Construct Class instance only if it's new or changed
                    if (_clazz == null || !_clazz.getName().equals(className)) {
                        if (classMapName != null) {
                            _deletedObjects.put(classMapName, classCount);
                        }
                        _clazz = (Class<? extends SailPointObject>) Class.forName(className);
                        classMapName = _clazz.getSimpleName();
                        if (classMapName == null) {
                            // should never be unless this starts pruning anonymous or local class objects
                            classMapName = "none";
                        }
                        classCount = 0;
                    }
                    SailPointObject o = _context.getObjectById(_clazz, (String)id);
                    if (o != null) {
                        arnold.deleteObject(o);
                        counter++;
                        classCount++;
                    } else {
                        // I think a missing object one wanted to delete anyways merits 
                        // nothing more serious than a debug message
                        log.debug(objId + " was not found");
                    }
                    if (counter % DECACHE_EVERY == 0) {
                        _context.decache();
                    }
                } catch (ClassNotFoundException cne) {
                    _result.addMessage(Message.error(MessageKeys.ERR_CLASS_NOT_FOUND, className));
                    throw new GeneralException(cne);
                }
            }
        }
        
        if (classMapName != null) {
            _deletedObjects.put(classMapName, classCount);
        }

        return counter;
    }
    
    private boolean isTerminated() {
        return _terminated;
    }
    
    @Override
    public boolean terminate() {
        _terminated = true;
        return true;
    }

}
