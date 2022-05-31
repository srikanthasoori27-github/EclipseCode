package sailpoint.tools;

import java.io.Serializable;

/**
 * 
 * @author Tapash
 *
 * Motivation:
 * Earlier Use: There are a lot of places in the UI where we do things like xxxDTO.getProperty1()
 * where property1 is something that needs to be loaded only when necessary.
 * The way it was being handled was if (property1 == null) we load the property,
 * otherwise we return property1.
 * 
 * Problem with earlier use: The problem with this approach is that what happens if the value really *is null*?
 * We will end up calling the load method over and over again beause the null check 
 * is not enough. We need to check if the properly was loaded.
 * 
 * This class tries to solve the problem. The load method will be called only when getValue() is called 
 * for the first time. 
 *  
 *  
 * @param <T> The Value class that needs to be lazy loaded
 */
@SuppressWarnings("serial")
public class LazyLoad<T> implements Serializable {
    
    public interface ILazyLoader<T> {
        T load() throws GeneralException;
    }
    
    private boolean loaded;
    private ILazyLoader<T> loader;
    private T value;
    
    
    public LazyLoad(ILazyLoader<T> loader) {
        this.loader = loader;
    }
    
    public T getValue() throws GeneralException {
        if (!this.loaded) {
            this.value = this.loader.load();
            this.loaded = true;
        }
        return this.value;
    }

    public void setValue(T val) {
        this.value = val;
    }
}
