package sailpoint.reporting;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.sf.jasperreports.engine.JRVirtualizable;
import net.sf.jasperreports.engine.fill.JRAbstractLRUVirtualizer;
import net.sf.jasperreports.engine.util.JRSwapFile;

/**
 * A swap file based virtualizer that uses the JRSwapFile
 * object.
 */
public class SwapFileVirtualizer extends JRAbstractLRUVirtualizer {

    /**
     * The file that is holding our swap data.
     */
    private final JRSwapFile _swap;

    /**
     * Map of handles to the swap file, pointers to the virtualized objects.
     */
    private final Map<String,JRSwapFile.SwapHandle> _handles;
 
    /**
     * Create a vitrualizer that will create a swap file in the specified directory.
     */
    public SwapFileVirtualizer(int virtualizerSize, int blockSize, int growthRateInBlocks, String directory) {
        super(virtualizerSize);
        _swap = new JRSwapFile(directory, blockSize, growthRateInBlocks);
        _handles = Collections.synchronizedMap(new HashMap<String,JRSwapFile.SwapHandle>());
        setReadOnly(false);
    } 

    protected void pageOut(JRVirtualizable object) throws IOException {
        if ( !_handles.containsKey(object.getUID()) ) {
            try {
                ByteArrayOutputStream bout = new ByteArrayOutputStream(3000);
                writeData(object, bout);
                byte[] data = bout.toByteArray();
                JRSwapFile.SwapHandle handle = _swap.write(data);
                _handles.put(object.getUID(), handle);
            } catch (IOException ex) {
                this.cleanup();
                throw ex;
            }
        } else {
            if ( !isReadOnly(object) ) {
                throw new IllegalStateException("Cannot virtualize data because the data for object UID \"" + object.getUID() + "\" already exists.");
            }
        }
    }

    protected void pageIn(JRVirtualizable object) throws IOException {
        JRSwapFile.SwapHandle handle = (JRSwapFile.SwapHandle) _handles.get(object.getUID());
        byte[] data = _swap.read(handle, !isReadOnly(object));
        readData(object, new ByteArrayInputStream(data));
		
        if ( !isReadOnly(object) ) {
            _handles.remove(object.getUID());
        }
    }

    /**
     * This is called as each virtualizable object is disposed from memory
     * in the objects finalize block.
     */
    protected void dispose(String id) {
        JRSwapFile.SwapHandle handle = (JRSwapFile.SwapHandle) _handles.remove(id);
        if ( handle != null ) {
            _swap.free(handle);
        }
    }
	
    /**
     * Disposes the swap file used.  This is called when the virtualizer's
     * finalize method is called.
     */
    public void cleanup() {
        _handles.clear();
        _swap.dispose();
    }
}
