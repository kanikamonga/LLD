package LLD.MiddlewareRouter.Trie;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

class TrieRouter implements Router {
	private TrieNode root = new TrieNode();
	
	@Override
	public void addRoute(String path, String result) {
		String[] parts = path.split("/");
		TrieNode node  = root;
		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}
			
			if (part.equals("*")) {
				if (node.wildcardChild == null) {
					node.wildcardChild = new TrieNode();
				}
				node = node.wildcardChild;
				break; // wildcard consumes the rest
			} else if (part.startsWith(":")) {
				if (node.paramChild == null) {
					node.paramChild = new TrieNode();
				}
				node.paramChild.paramName = part.substring(1);
				node                      = node.paramChild;
			} else {
				node = node.children.computeIfAbsent(part, k -> new TrieNode());
			}
		}
		node.result = result;
	}
	
	@Override
	public String callRoute(String path) {
		String[] parts = Arrays.stream(path.split("/"))
				.filter(p -> !p.isEmpty())
				.toArray(String[]::new);
		Map<String, String> params = new HashMap<>();
		TrieNode node = match(root, parts, 0, params);
		
		if (node == null || node.result == null) {
			throw new RuntimeException("No route found for " + path);
		}
		
		// Interpolate params into result
		String res = node.result;
		for (var entry : params.entrySet()) {
			res = res.replace(":" + entry.getKey(), entry.getValue());
		}
		return res;
	}
	
	private TrieNode match(TrieNode node, String[] parts, int index, Map<String, String> params) {
		if (node == null) {
			return null;
		}
		if (index == parts.length) {
			return node;
		}
		
		String part = parts[index];
		
		// 1. Exact static match
		TrieNode child = node.children.get(part);
		TrieNode res   = match(child, parts, index + 1, params);
		if (res != null && res.result != null) {
			return res;
		}
		
		// 2. Param match
		if (node.paramChild != null) {
			params.put(node.paramChild.paramName, part);
			res = match(node.paramChild, parts, index + 1, params);
			if (res != null && res.result != null) {
				return res;
			}
		}
		
		// 3. Wildcard match
		if (node.wildcardChild != null) {
			return node.wildcardChild;
		}
		
		return null;
	}
}
