package com.j7.session;

import java.security.Principal;
import java.util.HashMap;

import org.apache.catalina.Manager;
import org.apache.catalina.session.StandardSession;

public class J7Session extends StandardSession {

	private static final long serialVersionUID = 1L;
	protected static Boolean manualDirtyTrackingSupportEnabled = false;

	public static void setManualDirtyTrackingSupportEnabled(Boolean enabled) {
		manualDirtyTrackingSupportEnabled = enabled;
	}

	protected static String manualDirtyTrackingAttributeKey = "__changed__";

	public static void setManualDirtyTrackingAttributeKey(String key) {
		manualDirtyTrackingAttributeKey = key;
	}

	protected HashMap<String, Object> changedAttributes;
	protected Boolean dirty;

	public J7Session(Manager manager) {
		super(manager);
		resetDirtyTracking();
	}

	public Boolean isDirty() {
		return dirty || !changedAttributes.isEmpty();
	}

	public HashMap<String, Object> getChangedAttributes() {
		return changedAttributes;
	}

	public void resetDirtyTracking() {
		changedAttributes = new HashMap<String, Object>();
		dirty = false;
	}

	@Override
	public void setAttribute(String key, Object value) {
		if (manualDirtyTrackingSupportEnabled && manualDirtyTrackingAttributeKey.equals(key)) {
			dirty = true;
			return;
		}

		Object oldValue = getAttribute(key);
		if (((value != null) || (oldValue != null))
				&& (((value == null) && (oldValue != null)) || ((oldValue == null) && (value != null))
						|| !value.getClass().isInstance(oldValue) || !value.equals(oldValue))) {
			changedAttributes.put(key, value);
		}

		super.setAttribute(key, value);
	}

	@Override
	public void removeAttribute(String name) {
		dirty = true;
		super.removeAttribute(name);
	}

	@Override
	public void setId(String id) {
		this.id = id;
	}

	@Override
	public void setPrincipal(Principal principal) {
		dirty = true;
		super.setPrincipal(principal);
	}

}
