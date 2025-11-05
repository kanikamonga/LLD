package LLD.MiddlewareRouter.Trie;

import java.util.HashMap;
import java.util.Map;

class TrieNode {
	Map<String, TrieNode> children = new HashMap<>();
	TrieNode              paramChild;
	String                paramName;
	TrieNode              wildcardChild;
	String                result;
}
