package sailpoint.web.application;

import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import sailpoint.object.Application;

/**
 * used to validate the application.getDependencies() field
 * WARNING: this class is not thread safe.  Do not share instances of this class across threads.
 * @author chris.annino
 *
 */
public class DependencyValidator {
    
	/**
	 * the list of all applications, using a LIFO queue (AKA stack) here to ensure the last element 
	 * on the stack is the application we need to return to indicate .
	 */
	private Deque<Application> allApps = new LinkedList<Application>();
	
	public DependencyValidator() {}

	/**
	 * validates the application does not contain a cyclic dependency with
	 * its dependents.  implementation uses recursion to "flatten" the dependencies
	 * each application has on other applications. 
	 * @param app the application to validate there are no cyclic references
	 * @return a Set of applications containing all applications which have circular dependencies
	 */
	public Set<Application> checkCycles(Application app) {
	    // ensure all Applications are clear before recursing through the list of dependencies 
	    // allows for one instance of DependencyValidator to call checkCycles many times
	    allApps.clear();
		return checkCycles(app, app.getDependencies());
	}
	
	private Set<Application> checkCycles(Application app, List<Application> dependentApps) {
		Set<Application> cyclicApps = new HashSet<Application>(); 
		
		if (!allApps.contains(app)) {
			allApps.addFirst(app);
			
			if (dependentApps != null) {
				for (Application otherApp : dependentApps) {
				    // add all previous cycles to existing in cases where cycle overrides  
					cyclicApps.addAll( checkCycles(otherApp, otherApp.getDependencies()) );
				}
			}
		}
		else {
			// cycle has been detected
		    // NOTE: we are using a getFirst and not a removeFirst as we want to keep the application
		    //       in the stack of all applications
			cyclicApps.add(allApps.getFirst());
		}
		
		return cyclicApps;
	}
	
}
