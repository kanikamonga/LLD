package LLD.MiddlewareRouter.Trie;

interface Router {
    void addRoute(String path, String result);
    String callRoute(String path);
}

