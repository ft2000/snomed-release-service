package org.ihtsdo.buildcloud.controller;

import org.ihtsdo.buildcloud.controller.helper.HypermediaGenerator;
import org.ihtsdo.buildcloud.entity.User;
import org.ihtsdo.buildcloud.service.security.SecurityHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/user")
public class UserController {

	@Autowired
	private HypermediaGenerator hypermediaGenerator;

	@RequestMapping
	@ResponseBody
	public Map<String, Object> getCurrentUser(HttpServletRequest request) {
		User authenticatedUser = SecurityHelper.getRequiredUser();
		Map<String, Object> userRepresentation = getUserRepresentation(authenticatedUser);
		boolean currentResource = true;
		return hypermediaGenerator.getEntityHypermedia(userRepresentation, currentResource, request);
	}

	private Map<String, Object> getUserRepresentation(User user) {
		Map<String, Object> representation = new HashMap<>();
		String username = user.getUsername();
		representation.put("username", username);
		boolean authenticated = !username.equals(User.ANONYMOUS_USER);
		representation.put("authenticated", authenticated);
		return representation;
	}

}
