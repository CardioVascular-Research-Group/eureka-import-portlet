package edu.jhu.cvrg.waveform.backing;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liferay.faces.portal.context.LiferayFacesContext;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.util.HttpUtil;
import com.liferay.portal.model.User;
import com.liferay.portal.util.PortalUtil;

import edu.jhu.cvrg.data.util.DataStorageException;
import edu.jhu.cvrg.waveform.service.controller.EurekaController;
import edu.jhu.cvrg.waveform.service.model.EurekaInput;
import edu.jhu.cvrg.waveform.utility.ResourceUtility;

/**
 * <strong>Eureka import backing bean</strong><br>
 * 
 * Designed to be accessed as a logged user or a liferay guest. This class have the objective to handle the submitted JSON data, validate it and import it as a node in the virtual tree structure.<br><br>
 * 
 * 
 * @author avilard4
 *
 */
@ViewScoped
@ManagedBean(name = "EurekaImportBean")
public class EurekaImportBacking extends BackingBean{
	
	private boolean error = false;
	/**
	 * Message to be showed to the user, could be a INFO or ERROR
	 */
	private String message;
	
	/**
	 * Used to hold the auto redirect URL, based on the resquest action
	 */
	private String redirectURL;

	/**
	 * Temporary local session to handle the un-identified resquest.
	 */
	private static Map<String, EurekaInput> session;
	/**
	 * Clean-up interval for the temporary local session. (in minutes) 
	 */
	private static final int CLEANER_INTERVAL = 30;
	
	
	@PostConstruct
	public void init() {
		this.getLog().info("Initializing MyBean");
		LiferayFacesContext context = LiferayFacesContext.getInstance();
		HttpServletRequest request = PortalUtil.getHttpServletRequest(context.getPortletRequest());
		
		HttpServletResponse response = PortalUtil.getHttpServletResponse(context.getPortletResponse());
		response.addHeader("Access-Control-Allow-Origin", "https://eureka.cvrgrid.org");
		
		EurekaInput input = null;
		User loggedUser = null;
		
		try {
			//retreive the json parameter
			String jsonString = PortalUtil.getOriginalServletRequest(request).getParameter("json");
			loggedUser = ResourceUtility.getCurrentUser();
			
			if(jsonString != null){
				//Parse to JSON object
				ObjectMapper mapper = new ObjectMapper();
				input = mapper.readValue(jsonString, EurekaInput.class);	
				
			}else{
				if(loggedUser != null){
					//retrieve the requested input from the temporary session
					input = EurekaImportBacking.getSession().get(loggedUser.getScreenName());
					EurekaImportBacking.getSession().remove(loggedUser.getScreenName());
				}
			}
		} catch (JsonParseException e) {
			error = true;
			message = context.getMessage("error.json");
			this.getLog().error(message + " = "+e.getMessage());
		} catch (JsonMappingException e) {
			error = true;
			message = context.getMessage("error.json");
			this.getLog().error(message + " = "+e.getMessage());
		} catch (IOException e) {
			error = true;
			message = context.getMessage("error.json");
			this.getLog().error(message + " = "+e.getMessage());
		} catch (Exception e){
			error = true;
			message = context.getMessage("error.general");
			this.getLog().error(message + " = "+e.getMessage());
		}
		
		if(!error){
			if(input != null){
				try{
					this.getLog().info("User id: "+input.getUserId());
					if(loggedUser != null && loggedUser.getScreenName().equals(input.getUserId())){
						//logged user match with the requested data
						EurekaController.getInstance().eurekaImportProcess(input);
						message = context.getMessage("sucess.go.to.upload");
						redirectURL = "/web/guest/upload";
						
					}else{
						if(loggedUser == null){
							//No user, redirect to login
							message = context.getMessage("not.logged.go.to.login");
							redirectURL = HttpUtil.addParameter(context.getThemeDisplay().getURLSignIn(), "redirect", context.getThemeDisplay().getURLCurrent());
						}else{
							//the logged user not match. Perform the logout action.
							message = context.getMessage("logged.different.user");
							redirectURL = HttpUtil.addParameter("/c/portal/eureka/logout", "redirect", context.getThemeDisplay().getURLCurrent());
						}
						
						EurekaImportBacking.getSession().put(input.getUserId(), input);
						
					}
				} catch (SystemException e) {
					error = true;
					message = context.getMessage("error.user.initialize");
					this.getLog().error(message + " = "+e.getMessage());
				} catch (PortalException e) {
					error = true;
					message = context.getMessage("error.user.initialize");
					this.getLog().error(message + " = "+e.getMessage());
				} catch (DataStorageException e) {
					error = true;
					message = context.getMessage("error.import.process");
					this.getLog().error(message + " = "+e.getMessage());
				}
			}else{
				message = context.getMessage("nothing.to.import.go.to.upload");
				redirectURL = "/web/guest/upload";
			}
		}
		
	}
	
	public String getMessage(){
		return message;
	}
	
	/**
	 * The liferay redirect method, triggered by a primefaces remote command.
	 * @return null
	 */
	public String redirect(){
		
		try {
			LiferayFacesContext context = LiferayFacesContext.getInstance();
			context.getExternalContext().redirect(redirectURL);
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	/**
	 * This method return the temporary session map. If is null, initialize it and create the clean-up scheduled executor.
	 * 
	 * @return The temporary session map
	 */
	public static Map<String, EurekaInput> getSession(){
		if(session == null){
			session = new HashMap<String, EurekaInput>();
			ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
			scheduler.scheduleAtFixedRate(new Thread(){public void run(){ EurekaImportBacking.sessionCleanUp(); };}, CLEANER_INTERVAL, CLEANER_INTERVAL, TimeUnit.MINUTES);
		} 
		return session;
	}
	
	public static void sessionCleanUp(){
		if(session != null && !session.isEmpty()){
			for (String key : session.keySet()) {
				if(session.get(key).removeMe()){
					session.remove(key);
				}
			}
		}
	}

	public boolean isError() {
		return error;
	}
}