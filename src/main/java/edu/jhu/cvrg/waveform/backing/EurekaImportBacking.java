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

@ViewScoped
@ManagedBean(name = "EurekaImportBean")
public class EurekaImportBacking extends BackingBean{
	
	private boolean error = false;
	private String message;
	private String redirectURL;

	private static Map<String, EurekaInput> session;
	private static final int CLEANER_INTERVAL = 30;
	
	@PostConstruct
	public void init() {
		this.getLog().info("Initializing MyBean");
		LiferayFacesContext context = LiferayFacesContext.getInstance();
		HttpServletRequest request = PortalUtil.getHttpServletRequest(context.getPortletRequest());
		
		EurekaInput input = null;
		User loggedUser = null;
		
		try {
			
			String jsonString = PortalUtil.getOriginalServletRequest(request).getParameter("json");
			loggedUser = ResourceUtility.getCurrentUser();
			
			if(jsonString != null){
				ObjectMapper mapper = new ObjectMapper();
				input = mapper.readValue(jsonString, EurekaInput.class);	
				
			}else{
				if(loggedUser != null){
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
						EurekaController.getInstance().eurekaImportProcess(input);
						message = context.getMessage("sucess.go.to.upload");
						redirectURL = "/web/guest/upload";
						
					}else{
						if(loggedUser == null){
							message = context.getMessage("not.logged.go.to.login");
							redirectURL = HttpUtil.addParameter(context.getThemeDisplay().getURLSignIn(), "redirect", context.getThemeDisplay().getURLCurrent());
						}else{
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
	
	public String redirect(){
		
		try {
			LiferayFacesContext context = LiferayFacesContext.getInstance();
			context.getExternalContext().redirect(redirectURL);
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
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