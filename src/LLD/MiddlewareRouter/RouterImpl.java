package LLD.MiddlewareRouter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class RouterImpl implements Router {
	List<Route> routes = new ArrayList<>();
	
	@Override
	public void addRoute(String path, String result) {
		routes.add(new Route(path, result));
	}
	
	@Override
	public String callRoute(String path) {
		for (Route route : routes) {
			Map<String, String> params = route.match(path);
			if (params != null) {
				// Optionally interpolate params into result
				String res = route.result;
				for (var entry : params.entrySet()) {
					res = res.replace(":" + entry.getKey(), entry.getValue());
				}
				return res;
			}
		}
		throw new RuntimeException("No matching route for " + path);
	}
}
