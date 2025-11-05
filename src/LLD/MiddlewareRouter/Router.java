package LLD.MiddlewareRouter;

interface Router {
    void addRoute(String path, String result);
    String callRoute(String path);
}

