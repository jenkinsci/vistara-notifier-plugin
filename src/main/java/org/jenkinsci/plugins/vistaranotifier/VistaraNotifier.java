/*
 * The MIT License
 *
 * Copyright  2016 VistaraIT, Inc. All Rights Reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.vistaranotifier;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;

import javax.servlet.ServletException;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;

/**A class to notify Vistara about build status
 * @author Srini T
 *
 */
public class VistaraNotifier extends Notifier implements VistaraNotifierConstants {
	
    /**
     * Constructor
     */
    @DataBoundConstructor
    public VistaraNotifier() {
    }

    /* (non-Javadoc)
     * @see hudson.tasks.BuildStep#getRequiredMonitorService()
     */
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }
    
    /* (non-Javadoc)
     * @see hudson.tasks.BuildStepCompatibilityLayer#prebuild(hudson.model.AbstractBuild, hudson.model.BuildListener)
     */
    @Override
	public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
		return processEvent(build, listener, STARTED);
	}

    /* (non-Javadoc)
     * @see hudson.tasks.BuildStepCompatibilityLayer#perform(hudson.model.AbstractBuild, hudson.Launcher, hudson.model.BuildListener)
     */
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
    	String state = null;
        if((build.getResult() == null) || (!build.getResult().equals(Result.SUCCESS))) {
        	state = FAILED;
 		} else {
 			state = SUCCESS;
 		}
        return processEvent(build, listener, state);
    }

    /** Process build event
     * @param build
     * @param listener
     * @param state
     * @return
     */
    private boolean processEvent(final AbstractBuild<?, ?> build, final BuildListener listener, final String state) {
	   PrintStream logger = listener.getLogger();
       
	   VistaraDescriptor descriptor = getDescriptor();
       if (!descriptor.isEnabled()){
           logger.println("WARNING: Vistara Notification Disabled - Not configured.");
           return true; //NOTE: Don't fail build because of our notification...
       }
       
       try {
    	   VistaraNotifierClient apiClient = new VistaraNotifierClient(descriptor.getVistaraBaseURI(),
    			   descriptor.getVistaraClientId(), descriptor.getVistaraApiKey(), descriptor.getVistaraApiSecret());
    	   
    	   String alertPayload = VistaraNotifierUtils.prepareVistaraAlert(build, state);
   		   String response = apiClient.createAlert(alertPayload);
   		   logger.println("Vistara notifier response: " + response);
       } catch (Exception e){
           logger.println("Failed to send notification to Vistara, Reason: " + e.toString());
           //return false;
       }
       return true;
    }
    
    /* (non-Javadoc)
     * @see hudson.tasks.Notifier#getDescriptor()
     */
    @Override
    public VistaraDescriptor getDescriptor() {
        return (VistaraDescriptor)super.getDescriptor();
    }

    /**
     * @author Srini T
     *
     */
    @Extension
    public static final class VistaraDescriptor extends BuildStepDescriptor<Publisher> {
    	private String vistaraClientId;
		private String vistaraApiKey;
		private String vistaraApiSecret;
		private String vistaraBaseURI;
		private final String vistaraDefaultBaseURI = "https://api.vistara.io";
		
		 public VistaraDescriptor() {
	            load();
	        }
		/**
		 * @return
		 */
		public boolean isEnabled() {
			return getVistaraApiKey() != null && getVistaraApiSecret() != null && 
					getVistaraClientId() != null && getVistaraBaseURI() != null;
		}
		
		/**
		 * @return
		 */
		public String getVistaraDefaultBaseURI() {
		    return vistaraDefaultBaseURI;
		}
		 
		/**
		 * @return
		 */
		public String getVistaraClientId() {
			if(vistaraClientId == null || vistaraClientId.equals(EMPTY_STR)) {
				return null;
			}
			return vistaraClientId;
		}
		
		/**
		 * @return
		 */
		public String getVistaraApiKey() {
			if(vistaraApiKey == null || vistaraApiKey.equals(EMPTY_STR)) {
				return null;
			}
			return vistaraApiKey;
		}
		
		/**
		 * @return
		 */
		public String getVistaraApiSecret() {
			if(vistaraApiSecret == null || vistaraApiSecret.equals(EMPTY_STR)) {
				return null;
			}
			return vistaraApiSecret;
		}
		
		/**
		 * @return
		 */
		public String getVistaraBaseURI() {
		    if (vistaraBaseURI == null){
		        return vistaraBaseURI;
		    } else if(vistaraBaseURI.equals(EMPTY_STR)) {
		        return vistaraDefaultBaseURI;
		    } 
		    return vistaraBaseURI;
		}
		
		/* (non-Javadoc)
		 * @see hudson.tasks.BuildStepDescriptor#isApplicable(java.lang.Class)
		 */
		@SuppressWarnings("rawtypes")
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
		    return true;
		}
		
		/* (non-Javadoc)
		 * @see hudson.model.Descriptor#getDisplayName()
		 */
		public String getDisplayName() {
		    return VISTARA_DISPLAY_NAME;
		}
		
		/** Validate Vistara API key
		 * @param value
		 * @return
		 * @throws IOException
		 * @throws ServletException
		 */
		public FormValidation doCheckVistaraApiKey(@QueryParameter("vistaraApiKey") String value)
		    throws IOException, ServletException {
			String apiKey = value;
			if((apiKey != null) && (!apiKey.trim().equals(EMPTY_STR))) {
				apiKey = apiKey.trim();
			} else {
				apiKey = vistaraApiKey != null ? vistaraApiKey.trim() : null;
			}
		
			if((apiKey == null) || apiKey.equals(EMPTY_STR)) {
				return FormValidation.error(API_KEY_ERR_MSG);
		    } else {
		        return FormValidation.ok();
		    }
		}
		
		/** Validate Vistara API secret
		 * @param value
		 * @return
		 * @throws IOException
		 * @throws ServletException
		 */
		public FormValidation doCheckVistaraApiSecret(@QueryParameter("vistaraApiSecret") String value)
		    throws IOException, ServletException {
			String apiSecret = value;
			if((apiSecret != null) && (!apiSecret.trim().equals(EMPTY_STR))) {
				apiSecret = apiSecret.trim();
			} else {
				apiSecret = vistaraApiSecret != null ? vistaraApiSecret.trim() : null;
			}
		
			if((apiSecret == null) || apiSecret.equals(EMPTY_STR)) {
				return FormValidation.error(API_SECRET_ERR_MSG);
		    } else {
		        return FormValidation.ok();
		    }
		}
		
		/** Validate Vistara client ID
		 * @param value
		 * @return
		 * @throws IOException
		 * @throws ServletException
		 */
		public FormValidation doCheckVistaraClientId(@QueryParameter("vistaraClientId") String value)
		    throws IOException, ServletException {
			String clientId = value;
			if((clientId != null) && (!clientId.trim().equals(EMPTY_STR))) {
				clientId = clientId.trim();
			} else {
				clientId = vistaraClientId != null ? vistaraClientId.trim() : null;
			}
		
			if((clientId == null) || clientId.equals("")) {
				return FormValidation.error(CLIENT_ID_ERR_MSG);
		    } else {
		        return FormValidation.ok();
		    }
		}
		
		/** Validate Vistara base URI
		 * @param value
		 * @return
		 * @throws IOException
		 * @throws ServletException
		 */
		public FormValidation doCheckVistaraBaseURI(@QueryParameter("vistaraBaseURI") String value)
		    throws IOException, ServletException {
			String url = value;
			if((url != null) && (!url.trim().equals(EMPTY_STR))) {
			    url = url.trim();
			} else {
			    url = vistaraBaseURI != null ? vistaraBaseURI.trim() : null;
			}
			
			try {
			    new URL(url);
			    return FormValidation.ok();
			} catch (Exception e) {
			    return FormValidation.error(BASE_URI_ERR_MSG);
			}
		}
		
		/* (non-Javadoc)
		 * @see hudson.model.Descriptor#configure(org.kohsuke.stapler.StaplerRequest, net.sf.json.JSONObject)
		 */
		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
		    vistaraClientId = formData.getString(CLIENT_ID_PARAM);
			vistaraApiKey = formData.getString(API_KEY_PARAM);
			vistaraApiSecret = formData.getString(API_SECRET_PARAM);
		    vistaraBaseURI = formData.getString(BASE_URI_PARAM);
		    save();
		    return super.configure(req, formData);
		}
    }
}