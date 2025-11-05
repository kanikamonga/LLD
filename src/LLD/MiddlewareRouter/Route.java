package LLD.MiddlewareRouter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Route {
	String       pathPattern;
	String       result;
	Pattern      regex;
	List<String> paramNames = new ArrayList<>();
	
	Route(String pathPattern, String result) {
		this.pathPattern = pathPattern;
		this.result      = result;
		
		// Convert pathPattern → regex
		// Example: "/user/:id/*" → "^/user/([^/]+)(/.*)?$"
		StringBuilder regexBuilder = new StringBuilder("^");
		for (String part : pathPattern.split("/")) {
			if (part.isEmpty()) {
				continue;
			}
			regexBuilder.append("/");
			if (part.equals("*")) {
				regexBuilder.append(".*");
			} else if (part.startsWith(":")) {
				regexBuilder.append("([^/]+)");
				paramNames.add(part.substring(1));
			} else {
				regexBuilder.append(Pattern.quote(part));
			}
		}
		regexBuilder.append("$");
		this.regex = Pattern.compile(regexBuilder.toString());
	}
	
	Map<String, String> match(String path) {
		Matcher m = regex.matcher(path);
		if (!m.matches()) {
			return null;
		}
		
		Map<String, String> params = new HashMap<>();
		for (int i = 0; i < paramNames.size(); i++) {
			params.put(paramNames.get(i), m.group(i + 1));
		}
		return params;
	}
}
