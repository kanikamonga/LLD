package LLD.MiddlewareRouter;

public class Main {
    public static void main(String[] args) {
        Router router = new RouterImpl();

        // Simple route
        router.addRoute("/bar", "result");
        
        // Wildcard route
        router.addRoute("/foo/*", "foo matched");
        
        // Path params route
        router.addRoute("/user/:id/orders/:orderId", "User :id Order :orderId");

        System.out.println(router.callRoute("/bar")); 
        // "result"

        System.out.println(router.callRoute("/foo/anything/here"));
        // "foo matched"

        System.out.println(router.callRoute("/user/42/orders/99"));
        // "User 42 Order 99"
    }
}
