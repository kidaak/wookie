/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.wookie.manager.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.apache.wookie.Messages;
import org.apache.wookie.beans.Feature;
import org.apache.wookie.beans.Param;
import org.apache.wookie.beans.Participant;
import org.apache.wookie.beans.Preference;
import org.apache.wookie.beans.PreferenceDefault;
import org.apache.wookie.beans.SharedData;
import org.apache.wookie.beans.Whitelist;
import org.apache.wookie.beans.Widget;
import org.apache.wookie.beans.WidgetDefault;
import org.apache.wookie.beans.WidgetInstance;
import org.apache.wookie.beans.WidgetService;
import org.apache.wookie.beans.WidgetType;
import org.apache.wookie.manager.IWidgetAdminManager;
import org.apache.wookie.manifestmodel.IFeatureEntity;
import org.apache.wookie.manifestmodel.IManifestModel;
import org.apache.wookie.manifestmodel.IParamEntity;
import org.apache.wookie.manifestmodel.IPreferenceEntity;

/**
 * WidgetAdminManager
 * 
 * This class is responsible for administrative functions such as adding new widget types
 * and setting which widget is to be the default
 * 
 * @author Paul Sharples
 * @version $Id: WidgetAdminManager.java,v 1.2 2009-07-28 16:05:23 scottwilson Exp $
 */
public class WidgetAdminManager implements IWidgetAdminManager {
	
	static Logger _logger = Logger.getLogger(WidgetAdminManager.class.getName());
	protected Messages localizedMessages;

	public WidgetAdminManager(Messages localizedMessages) {
		this.localizedMessages = localizedMessages;	
	}
				
	/* (non-Javadoc)
	 * @see org.apache.wookie.manager.IWidgetAdminManager#addNewService(java.lang.String)
	 */
	public boolean addNewService(String serviceName) {
		WidgetService service = new WidgetService();
		service.setServiceName(serviceName);
		return service.save();
	}
	
	
	/* (non-Javadoc)
	 * @see org.apache.wookie.manager.IWidgetAdminManager#addNewWidget(java.lang.String, java.lang.String, java.lang.String, int, int)
	 */
	public void addNewWidget(IManifestModel model) {
		addNewWidget(model, null);
	}

	/* (non-Javadoc)
	 * @see org.apache.wookie.manager.IWidgetAdminManager#addNewWidget(java.lang.String, java.lang.String, java.lang.String, int, int, java.lang.String[])
	 */
	@SuppressWarnings("unchecked")
	public int addNewWidget(IManifestModel model, String[] widgetTypes) {
		// NOTE: we pass the whole model here, so that we can create all the DB hooks more easily.
		// FOR now just use the first name, description, etc elements found in the manifest.
		int newWidgetIdx = -1;			
		Widget widget;
		widget = new Widget();												
		widget.setWidgetTitle(model.getFirstName());
		widget.setWidgetDescription(model.getFirstDescription());
		widget.setWidgetAuthor(model.getAuthor());
		widget.setWidgetIconLocation(model.getFirstIconPath());
		widget.setUrl(model.getContent().getSrc());
		widget.setGuid(model.getIdentifier());
		widget.setHeight(model.getHeight());
		widget.setWidth(model.getWidth());
		widget.setVersion(model.getVersion());
		widget.save();	
		WidgetType widgetType;
		if (widgetTypes!=null){
			for(int i=0;i<widgetTypes.length;i++){
				widgetType = new WidgetType();
				widgetType.setWidgetContext(widgetTypes[i]);
				widgetType.setWidget(widget);
				widget.getWidgetTypes().add(widgetType);
				widgetType.save();
			}
		}
		newWidgetIdx = widget.getId();

		// Save default preferences				
		for(IPreferenceEntity prefEntity : model.getPrefences()){
			PreferenceDefault prefenceDefault = new PreferenceDefault();
			prefenceDefault.setPreference(prefEntity.getName());
			prefenceDefault.setValue(prefEntity.getValue());
			prefenceDefault.setReadOnly(prefEntity.isReadOnly());
			prefenceDefault.setWidget(widget);
			prefenceDefault.save();
		}

		// Save Features
		for(IFeatureEntity featureEntity: model.getFeatures()){
			Feature feature = new Feature();
			feature.setFeatureName(featureEntity.getName());
			feature.setRequired(featureEntity.isRequired());
			feature.setWidget(widget);
			feature.save();			
			// now attach all parameters to this feature.
			for(IParamEntity paramEntity : featureEntity.getParams()){
				Param param = new Param();
				param.setParameterName(paramEntity.getName());
				param.setParameterValue(paramEntity.getValue());
				param.setParentFeature(feature);
				param.save();
			}
		}

		return newWidgetIdx;	       
	}
	
	/* (non-Javadoc)
	 * @see org.apache.wookie.manager.IWidgetAdminManager#addWhiteListEntry(java.lang.String)
	 */
	public boolean addWhiteListEntry(String uri) {
		Whitelist list = new Whitelist();
		list.setfUrl(uri);
		return list.save();
	}
	
	/* (non-Javadoc)
	 * @see org.apache.wookie.manager.IWidgetAdminManager#deleteWidgetDefaultById(int)
	 */
	public void deleteWidgetDefaultById(int widgetKey){		
		WidgetDefault[] widgetDefault = WidgetDefault.findByValue("widgetId", widgetKey);
		if (widgetDefault.length == 1) widgetDefault[0].delete();
	}
	
	/* (non-Javadoc)
	 * @see org.apache.wookie.manager.IWidgetAdminManager#deleteWidgetDefaultByIdAndServiceType(int, java.lang.String)
	 */
	public void deleteWidgetDefaultByIdAndServiceType(int widgetKey, String serviceType){
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("widgetId", widgetKey);
		map.put("widgetContext", serviceType);
		WidgetDefault[] widgetDefaults;
		widgetDefaults = WidgetDefault.findByValues(map);
		WidgetDefault.delete(widgetDefaults);
	}
	
	/* (non-Javadoc)
	 * @see org.apache.wookie.manager.IWidgetAdminManager#deleteWidgetDefaultByServiceName(java.lang.String)
	 */
	public void deleteWidgetDefaultByServiceName(String serviceName){
		WidgetDefault[] widgetDefaults = WidgetDefault.findByValue("widgetContext", serviceName);
		WidgetDefault.delete(widgetDefaults);
	}
	
	/* (non-Javadoc)
	 * @see org.apache.wookie.manager.IWidgetAdminManager#doesServiceExistForWidget(int, java.lang.String)
	 */
	public boolean doesServiceExistForWidget(int dbkey, String serviceType){
		Widget widget = Widget.findById(Integer.valueOf(dbkey));
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("widget", widget);
		map.put("widgetContext", serviceType);
		WidgetType[] types = WidgetType.findByValues(map);
		if (types == null || types.length !=1) return false;
		return true;					
	}
	
	/* (non-Javadoc)
	 * @see org.apache.wookie.manager.IWidgetAdminManager#doesWidgetAlreadyExistInSystem(java.lang.String)
	 */
	public boolean doesWidgetAlreadyExistInSystem(String guid){
		Widget[] widget = Widget.findByValue("guid", guid);
		if (widget == null || widget.length!=1) return false;
		return true;		
	}
	
	/* (non-Javadoc)
	 * @see org.apache.wookie.manager.IWidgetAdminManager#isWidgetMaximized(int)
	 */
	public boolean isWidgetMaximized(int dbKey){
		Widget widget = null;
		widget = Widget.findById(dbKey);
		if (widget == null) return false;
		return widget.isMaximize();
	}
	
	/* (non-Javadoc)
	 * @see org.apache.wookie.manager.IWidgetAdminManager#printOutAllWidgets(org.apache.wookie.manager.IWidgetAdminManager)
	 */	
	@SuppressWarnings("unchecked")
	public void printOutAllWidgets(IWidgetAdminManager magr){				
		Widget[] widgets = Widget.findAll();
	    for (int i = 0; i < widgets.length; i++) {
	        Widget theWidget = (Widget) widgets[i];		        
	        _logger.debug(
	        				   "\n\t Name: " + theWidget.getWidgetTitle() +
	        				   "\n\t URL: " + theWidget.getUrl() +
	                           "\n\t Height: " + theWidget.getHeight() +		          
	                           "\n\t width: " + theWidget.getWidth() + "\n\t Types:");
	        
	        Set<WidgetType> types = theWidget.getWidgetTypes();
	        WidgetType[] widgetTypes = types.toArray(new WidgetType[types.size()]);
	        for(int j=0;j<widgetTypes.length;j++){
	        	_logger.debug("\n\t "+widgetTypes[j].getWidgetContext());
	        }			     
	    }		    
	}
	
	
	/* (non-Javadoc)
	 * @see org.apache.wookie.manager.IWidgetAdminManager#removeServiceAndReferences(int)
	 */
	public boolean removeServiceAndReferences(int serviceId){
		WidgetService service = WidgetService.findById(Integer.valueOf(serviceId));
		String serviceName = service.getServiceName();
		
		// if exists, remove from widget default table
		deleteWidgetDefaultByServiceName(serviceName);
		
		// delete from the widget service table
		service.delete();	
		// remove any widgetTypes for each widget that match
		Widget[] widgets = Widget.findByType(serviceName);
		if (widgets == null||widgets.length==0) return true;
		for(Widget widget : widgets){
			// remove any widget types for this widget
			Set<?> types = widget.getWidgetTypes();
		    WidgetType[] widgetTypes = types.toArray(new WidgetType[types.size()]);		        
		    for(int j=0;j<widgetTypes.length;++j){	
		    	if(serviceName.equalsIgnoreCase(widgetTypes[j].getWidgetContext())){
		    		widgetTypes[j].delete();
		    	}
			}
		}					
		return true;
	}	
	
	/* (non-Javadoc)
	 * @see org.apache.wookie.manager.IWidgetAdminManager#removeSingleWidgetType(int, java.lang.String)
	 */
	public boolean removeSingleWidgetType(int widgetId, String widgetType) {
		boolean response = false;	
		Widget widget = Widget.findById(Integer.valueOf(widgetId));
		// remove any widget types for this widget
		Set<?> types = widget.getWidgetTypes();
        WidgetType[] widgetTypes = types.toArray(new WidgetType[types.size()]);		        
        for(int j=0;j<widgetTypes.length;++j){						
    		if(widgetType.equalsIgnoreCase(widgetTypes[j].getWidgetContext())){
    			// BUG FIX
    			// Using only the deleteObject method meant that
    			// the set still contained this widgetType.
    			// So we also remove it from the list
    			types.remove(widgetTypes[j]);
    			widgetTypes[j].delete();
    			response = true;
    		}
		}
        // if it exists as a service default, then remove it
        deleteWidgetDefaultByIdAndServiceType(widgetId, widgetType);
        return response;
	}
	
	/* (non-Javadoc)
	 * @see org.apache.wookie.manager.IWidgetAdminManager#removeWhiteListEntry(int)
	 */
	public boolean removeWhiteListEntry(int entryId) {
		Whitelist entry = Whitelist.findById(Integer.valueOf(entryId));
		return entry.delete();
	}
	
	/* (non-Javadoc)
	 * @see org.apache.wookie.manager.IWidgetAdminManager#removeWidgetAndReferences(int)
	 */
	public boolean removeWidgetAndReferences(int widgetId){
		// get the widget
		Widget widget = Widget.findById(Integer.valueOf(widgetId));
		// remove any defaults for this widget
		deleteWidgetDefaultById(widgetId);
		
		if(widget==null) return false;
		// find any widget instances for this widget
		WidgetInstance[] instances = WidgetInstance.findByValue("widget", widget);		
		// try to remove prefs, shareddata and then the instances
		for(WidgetInstance inst : instances){
			SharedData.delete(SharedData.findByValue("widgetInstance", inst));
			Preference.delete(Preference.findByValue("widgetInstance", inst));
			Participant.delete(Participant.findByValue("widgetInstance", inst));
			inst.delete();
		}
		// remove any widget types for this widget
		Set<?> types = widget.getWidgetTypes();
        WidgetType[] widgetTypes = types.toArray(new WidgetType[types.size()]);		        
        for(int j=0;j<widgetTypes.length;++j){	
        	widgetTypes[j].delete();
		}
        
        //Delete any PreferenceDefaults
        PreferenceDefault.delete(PreferenceDefault.findByValue("widget", widget));
        
        // next do the features & children params
        for(Feature feature :Feature.findByValue("widget", widget)){
        	Param.delete(Param.findByValue("parentFeature", feature));
        	feature.delete();
        }
        
		// remove the widget itself
        widget.delete();
		return true;
	} 
	
	
	
	
	/* (non-Javadoc)
	 * @see org.apache.wookie.manager.IWidgetAdminManager#setDefaultWidget(int, java.lang.String)
	 */
	public void setDefaultWidget(int key, String widgetType){
        boolean found=false;
		// does it already exist in the widgetdefault table?
		WidgetDefault[] currentDefaults = WidgetDefault.findAll();
		for(int i=0;i<currentDefaults.length;i++){
			if(currentDefaults[i].getWidgetContext().equalsIgnoreCase(widgetType)){   
				// found it so update to new widget id
				currentDefaults[i].setWidgetId(key);
				currentDefaults[i].save();
				found=true;
			}
		}
		// didnt find it already set, so add new one
		if(!found){
			WidgetDefault wd = new WidgetDefault();
			wd.setWidgetContext(widgetType);
			wd.setWidgetId(key);	
			wd.save();
		}
	}
	
	/* (non-Javadoc)
	 * @see org.apache.wookie.manager.IWidgetAdminManager#setWidgetTypesForWidget(int, java.lang.String[], boolean)
	 */
	@SuppressWarnings("unchecked")
	public void setWidgetTypesForWidget(int dbKey, String[] widgetTypes, boolean maximize){
		Widget widget = Widget.findById(dbKey);
		if(maximize){
			widget.setMaximize(maximize);
			widget.save();
		}

		WidgetType widgetType;
		if (widgetTypes!=null){
			for(int i=0;i<widgetTypes.length;i++){	
				if(!doesServiceExistForWidget(widget.getId(), widgetTypes[i])){
					widgetType = new WidgetType();
					widgetType.setWidgetContext(widgetTypes[i]);
					widgetType.setWidget(widget);
					widget.getWidgetTypes().add(widgetType);
					widgetType.save();						
				}
			}
		}			
	}

	/* (non-Javadoc)
	 * @see org.apache.wookie.manager.IWidgetAdminManager#getWidgetGuid(int)
	 */
	public String getWidgetGuid(int dbKey) {
		//Widget widget = Widget.findById(String.valueOf(dbKey));
		Widget widget = Widget.findById(Integer.valueOf(dbKey));
		return widget.getGuid();
	}

}
