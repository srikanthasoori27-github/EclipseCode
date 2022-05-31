/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Argument;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Reflection;
import sailpoint.tools.Util;

public class BaseTaskBean<E extends SailPointObject> extends BaseObjectBean
{
    private static final Log log = LogFactory.getLog(BaseTaskBean.class);
    
	public BaseTaskBean() {
		super();
	}

	// TODO: prune all of these if possible, defId can go on request,
	// and so can templateDef.. Argform... not sure..
	protected void cleanSession() {
		getSessionScope().remove(getSessionKey());
		getSessionScope().remove("editForm:templateDef");
		getSessionScope().remove("editForm:defId");
		getSessionScope().remove("editForm:argForm");
	}

	public String cancel() throws GeneralException {
		cleanSession();
		return "ok";
	}

	private final String PREFIX = "sailpoint.object.";
	protected Class loadClass(String type) {
		String name = type;

		String className = null;
		if ( name.startsWith(PREFIX) ) {
			className = name;
		} else {
			className = PREFIX + name;
		} 

		Class clazz = null;
		try {
			clazz = Class.forName(className);
		} catch (Exception e) { }

		return clazz;
	}

	public static void addExceptionToContext(Throwable th) {
		String errorMessage = formatException(th);
		FacesContext facesCtx = FacesContext.getCurrentInstance();
		FacesMessage msg = new FacesMessage(FacesMessage.SEVERITY_ERROR,
				errorMessage, errorMessage);
		facesCtx.addMessage("editForm", msg);
	}

	protected static String formatException(Throwable top) {
		StringBuffer b = new StringBuffer();
		if ( top != null ) {
			Throwable cause = top.getCause();
			if ( cause != null ) {
				String msg = cause.getMessage();
				b.append(msg);
				Throwable nested = cause.getCause();
				while ( nested != null ) {
					b.append("...");
					b.append(nested.getMessage());
					nested = nested.getCause();
				}
			} else {
				b.append(top.toString());
			}            
		}
		return b.toString();
	}

	public static boolean isEmpty(String val) {
		boolean isEmpty = true;
		if ( val != null ) {
			if ( val.length() > 0 ) {
				isEmpty = false;
				if ( ( val.length() == 1 ) 
						&& ( Character.isSpaceChar(val.charAt(0) ) ) ) 
					isEmpty = true;
			}
		}
		return isEmpty;
	}

	///////////////////////////////////////////////////////////////////////////
	//
	// Argument inner class
	//
	///////////////////////////////////////////////////////////////////////////

	public class ArgValue {

		/**
		 * Special value that may be used in selectItems to indiciate
		 * that the actual value should be null.  Isn't there an easier
		 * way to do this??
		 */
		public static final String NULL_VALUE = "*null*";

		private Object _value;
		private Argument _argument;

		/**
		 * This is a special option used with the Tomahawk date picker.
		 * Since the component defaults to the current date and always
		 * posts we have no way of representing a "null" date.  Instead
		 * you have to check an extra checkbox to enable using
		 * the date value.
		 */
		private boolean _bound;

		public Argument getArgument() {
			return _argument;
		}

		public void setArgument(Argument argument) {
			_argument = argument;
		}

		public String getName() {
			return (_argument !=null ) ? _argument.getName() : "no_name";
		}

		public Object getObjectValue() {
			return _value;
		}

		public void setObjectValue(Object value) {
			_value = value;
		}

		public String getValue() {
		    if (_value instanceof Map) {
		        _value = getMapAsString((Map<String, Object>)_value);
		    }
			return (String)_value;
		}

		private String getMapAsString(Map<String, Object> map) {
		    Set<String> keys = map.keySet();
		    StringBuilder mapString = new StringBuilder();
		    for (String key : keys) {
		        String value = Util.otos(map.get(key));
		        if (mapString.length() > 0) {
		            mapString.append(",");
		        }
		        mapString.append(key).append(",").append(value);
		    }
		    return mapString.toString();
		}
		
		public void setValue(String value) {

			// KLUDGE: The auto-generated selectOnMenu fields
			// for object selection have an initial item with label
			// "-- Select an Object --" and a special value.
			// I tried to get this to be just null, but that caused
			// other problems.  Sort out someday...jsl

		    // removed with caution 'cause it seems like empty strings
		    // are being handles like nulls just fine - DHC
			//if (NULL_VALUE.equals(value)) 
				//value = null;

			_value =  value;
		}

        /**
         * Returns object value. If value is null, an empty will be returned.
         * @return
         */
        public String getNullSafeValue() {
			return _value != null ? (String)_value : NULL_VALUE;
		}

        /**
         * Sets value. If the submitted value is an empty string
         * this method assumes the value should be null.
         * @param value
         */
		public void setNullSafeValue(String value) {
			_value =  NULL_VALUE.equals(value) ? null : value;
		}

		public boolean isBound() {
			return _bound;
		}

		public void setBound(boolean b) {
			_bound = b;
		}

		public Boolean getBooleanValue() {            
			Boolean value = new Boolean(false);
			if ( _value != null ) {
				value = new Boolean((String)_value);
			}
			return value;
		}

		public void setBooleanValue(Boolean checkbox) {
			if ( checkbox != null ) { 
				_value = checkbox.toString();
			} else {
				_value = "false";
			}
		}

		public Date getDateValue() {            
			Date value = new Date();
			if ( _value != null ) {
				try {
					value = Util.stringToDate((String)_value);
				} catch(Exception e){ };
			}
			return value;
		}

		public void setDateValue(Date date) {
			if ( date != null ) {
				_value = Util.dateToString(date);
			} else {
				_value = null;
			}
			
		}

		public void setObjectListValue(List value) {
			if ( value != null ) {
				_value = Util.listToCsv(value);
			} else {
				_value = null;
			}   
		}

        public void setObjectJsonValue(String value) throws GeneralException {
            if (value == null || "".equals(value) || "[]".equals(value)){
                _value = null;
            } else {
                try {
                    Class<?> clazz = Class.forName(_argument.getType());
                    if (_argument.isMulti()) {
                    	_value = JsonHelper.listFromJson(clazz, value);
					} else {
                    	_value = JsonHelper.fromJson(clazz, value);
					}
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("Could not handle argument of type " + _argument.getType());
                }
            }     
        }

        public void setObjectCustomSerializer(String value) {
            try {
                Class<?> clazz = Class.forName(_argument.getType());
                ICustomSerializer serializer = (ICustomSerializer) Reflection.newInstance(clazz);
                _value = serializer.deserialize(value, getContext());
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Could not handle argument of type " + _argument.getType());
            } catch (GeneralException e) {
                throw new RuntimeException("Could not deserialize value: " + value);
            }
        }

        public String getObjectCustomSerializer() {
            try {
                Class<?> clazz = Class.forName(_argument.getType());
                ICustomSerializer serializer = (ICustomSerializer) Reflection.newInstance(clazz);
                return serializer.serialize(_value, getContext());
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Could not handle argument of type " + _argument.getType());
            } catch (GeneralException e) {
                throw new RuntimeException("Could not serialize value: " + _value);
            }
        }

        public List getObjectListNameValue() {
            //just return the id list
            return getObjectListValue();
        }

        //Bug#14889
        //converts list of id to names.
        public void setObjectListNameValue(List value) {
            if ( value != null ) {
                //convert id list to names
                List<String> nameList = new ArrayList<String>();
                try {
                    Class clazz = loadClass(_argument.getType());

                    if (clazz!=null) {
                        for(Object id : value) {
                            try {
                                SailPointObject object = (SailPointObject)getContext().getObjectById(clazz, (String)id);

                                if(object!=null) {
                                    nameList.add(object.getName());
                                }
                            } catch(GeneralException ge) {}
                        }
                        _value = Util.listToCsv(nameList);
                    } else {
                        _value = Util.listToCsv(value);
                    }
                } catch (Exception e) {
                    if (log.isErrorEnabled()) {
                        log.error(e.getMessage(), e);
                    }
                }
            } else {
                _value = null;
            }   
        }

        public void setObjectNameValue(String value) {
			if ( value != null ) {
				//Convert value to Object name
				String name = null;
				try {
					Class clazz = loadClass(_argument.getType());

					if (clazz!=null) {
						try {
							SailPointObject object = (SailPointObject)getContext().getObjectByName(clazz, value);

							if(object!=null) {
								name = object.getName();
							}
						} catch(GeneralException ge) {}

						_value = name;
					} else {
						_value = value;
					}
				} catch (Exception e) {
					if (log.isErrorEnabled()) {
						log.error(e.getMessage(), e);
					}
				}
			} else {
				_value = null;
			}
		}

		public String getObjectNameValue() {
            return getValue();
		}


        public String getObjectJsonValue(){

            if (_value == null)
                return "";

            return JsonHelper.toJson(_value);
        }

		public List getObjectListValue() {
			List value = null;
			if ( _value != null ) {
				/** Sometimes we get object names instead of ids
				 * load the object and return its id so it works with the ui select lists - PH
				 */
				value = new ArrayList<String>();
				List<String> list = Util.csvToList((String)_value);

				try {

					Class clazz = loadClass(_argument.getType());

					if (clazz!=null) {
						for(String idOrName : list) {
							try {
								SailPointObject object = (SailPointObject)getContext().getObjectByName(clazz, idOrName);

								if(object!=null) {
									value.add(object.getId());
								} else {
									//must be id already
									value.add(idOrName);
								}

							}catch(GeneralException ge) {}
						}
					} else {
						return list;
					}
				} catch (Exception e) {
				    if (log.isErrorEnabled())
				        log.error(e.getMessage(), e);
				}
			}
			return value;
		}

        public List getObjectList() {
		    List value = null;
			if ( _value != null ) {
				/** Sometimes we get object names instead of ids
				 * load the object and return its id so it works with the ui select lists - PH
				 */
				value = new ArrayList<String>();
				List<String> list = Util.csvToList((String)_value);
				Class clazz = loadClass(_argument.getType());
				
				if (clazz!=null && list!=null) {
					for(String idOrName : list) {
						try {
							SailPointObject object = (SailPointObject)getContext().getObject(clazz, idOrName);

							if(object!=null)
								value.add(object);

						}catch(GeneralException ge) {}
					}
				} else {
					return list;
				}
			}
			return value;
		}

        public void setObjectIdValue(Object value) {
            _value = value;
        }

        public Object getObjectIdValue() {
            String value = (String)_value;
            if (_value != null) {
                Class clazz = loadClass(_argument.getType());
                String idOrName = (String)_value;
                if (clazz!= null) {
                    try {
                        SailPointObject obj = getContext().getObject(clazz, idOrName);
                        if (obj != null) {
                            value = obj.getId();
                        }
                    } catch(GeneralException ge) { }
                }
            }

            return value;
        }

        /** There are some instances of sailpoint objects that we need to handle specially with
		 * multi-suggest components in case the list of objects is too long. 
		 * @return
		 */
		public boolean isHandleSpecial() {
			boolean handleSpecial = false;
			if(getArgument().getType()!=null 
					&& (getArgument().getType().equals("Application"))){
				handleSpecial = true;
			}
			return handleSpecial;
		}

		public boolean isApplication() {
			boolean isApplication = false;
			if(getArgument().getType()!=null && getArgument().getType().equals("Application")){
				isApplication = true;
			}
			return isApplication;
		}
		
        public boolean isIdentity() {
            boolean isIdentity = false;
            if(getArgument().getType()!=null && getArgument().getType().equals("Identity")){
                isIdentity = true;
            }
            return isIdentity;
        }
        
        public boolean isGroup() {
            boolean isGroup = false;
            if(getArgument().getType()!=null && getArgument().getType().equals("GroupDefinition")){
                isGroup = true;
            }
            return isGroup;
        }

		public boolean isSailPointType() {
			Class clazz = loadClass(getArgument().getType());
			return (clazz != null ) ? true : false;
		}

		public Map<String,String> getObjectNames() throws GeneralException {
			Argument arg = getArgument();
			String type = arg.getType();
			String filterString = arg.getFilterString();
			SailPointObjectListBean bean = new SailPointObjectListBean(type,
					filterString);
			Map<String,String> names = bean.getDisplayableNames();
			return names;
		}

		public Map<String,String> getRuleNames() throws GeneralException {
			Argument arg = getArgument();
			String type = arg.getType();
			String filterString = arg.getFilterString();
			SailPointObjectListBean bean = new SailPointObjectListBean(type,
					filterString);
			Map<String,String> names = bean.getRuleNames();
			return names;
		}

		/**
		 * Special case for hand edited TaskDefinitions such as those in the
		 * unittests.  Here, we may need to reference other objects (Application,
		 * Rule, etc) but we have to use the name of the object in the Arguments 
		 * map rather than the id since we can't know what the id will be when
		 * the file is written.  
		 *
		 * But getObjectNames will return a map of id/name, assuming that if
		 * you were editing a task template in the UI, you would prefer to store
		 * the id so be immune to renames.  In order to get the current value to 
		 * show up as selected, we have to convert it to an id.
		 */
		public void resolve(SailPointContext context) 
		throws GeneralException {

			if (_value != null && _argument != null && _value instanceof String) {
				Class clazz = loadClass(_argument.getType());
				
				List<String> values = Util.csvToList((String)_value);
				List<String> objects = new ArrayList<String>();
				for(String val : values) {
					if (clazz != null && !ObjectUtil.isUniqueId(val)) {
						SailPointObject o = context.getObjectByName(clazz, val);
						if (o != null)
							objects.add(o.getId());
					}
				}
				
				if(objects.size()>1) {
					_value = Util.listToCsv(objects);
				} else if(!objects.isEmpty()){
					_value = objects.get(0);
				}
			}
		}
	}

	///////////////////////////////////////////////////////////////////////////
	//
	// SailPointObejectListBean - bean to display names of stored objects
	//
	///////////////////////////////////////////////////////////////////////////

	public class SailPointObjectListBean extends BaseListBean<SailPointObject> {
		private String _type;
		private String _filterString;

		public SailPointObjectListBean(String type, 
				String filterString) throws GeneralException {
			super();
			_type = type;
			_filterString = filterString;
			setScope();
		}  //  SailPointObjectListBean

		public String getType() {
			return _type;
		}

		public void setType(String type) {
			_type = type;
		}

		@SuppressWarnings("unchecked")
		private void setScope() throws GeneralException {
			String name = getType();
			Class<SailPointObject> clazz = loadClass(name);
			setScope(clazz);
		}

		public Map<String,String> getDisplayableNames() throws GeneralException {
			if ( _displayableNames == null ) {
				Map<String,String> m = new TreeMap<String,String>();

				List<String> l = new ArrayList<String>();
				l.add("id");
				l.add("name");

				QueryOptions ops = getQueryOptions();
				ops.setOrderBy("name");
				ops.setOrderAscending(true);

				Iterator<Object []> result = getContext().search(_scope, ops, l);
				if ( result != null ) {
					while ( result.hasNext() ) {
						Object[] row = result.next();
						if ( row != null && row.length == 2 ) {
							m.put(row[1].toString(), row[0].toString());
						}
					}  // while result.hasNext()
				}  // if result != null

				_displayableNames = m;
			}  // if _displayableNames == null

			return _displayableNames;
		}  // getDisplayableNames()

		public Map<String,String> getRuleNames() throws GeneralException {
			if ( _displayableNames == null ) {
				Map<String,String> m = new TreeMap<String,String>();

				List<String> l = new ArrayList<String>();
				l.add("id");
				l.add("name");

				QueryOptions ops = getQueryOptions();
				ops.setOrderBy("name");
				ops.setOrderAscending(true);

				Iterator<Object []> result = getContext().search(_scope, ops, l);
				if ( result != null ) {
					while ( result.hasNext() ) {
						Object[] row = result.next();
						if ( row != null && row.length == 2 ) {
							m.put(row[1].toString(), row[1].toString());
						}
					}
				}

				_displayableNames = m;
			}

			return _displayableNames;
		}

		public List<SailPointObject> getObjects() throws GeneralException {
			return super.getObjects();
		}

		public QueryOptions getQueryOptions() throws GeneralException {

			QueryOptions qo = new QueryOptions();
			Map<String,String> sortCols = getSortColumnMap();

			if (null != sortCols) {
				// Look for a sort column in the request.
				for (Map.Entry<String,String> entry : sortCols.entrySet()) {
					String direction = super.getRequestParameter(entry.getKey());
					if (null != direction) {
						qo.setOrderBy(entry.getValue());
						qo.setOrderAscending(direction.equalsIgnoreCase("ASC"));
						break;
					}
				}
			}

			if ( _filterString != null ) {                
				Filter filter = null;
				try {
					filter = Filter.compile(_filterString);
				} catch(Exception e) {}

				if ( filter != null ) qo.add(filter);
			}
			return qo;
		}
	} // ClassType

    public interface ICustomSerializer {
        String serialize(Object val, SailPointContext context) throws GeneralException;
        Object deserialize(String val, SailPointContext context) throws GeneralException;
    }
}
