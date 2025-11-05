package LLD.MiddlewareRouter.Trie;

public class Main {
    public static void main(String[] args) {
        Router router = new TrieRouter();

        router.addRoute("/bar", "result");
        router.addRoute("/foo/*", "foo matched");
        router.addRoute("/user/:id/orders/:orderId", "User :id Order :orderId");

        System.out.println(router.callRoute("/bar"));
        // "result"

        System.out.println(router.callRoute("/foo/anything/here"));
        // "foo matched"

        System.out.println(router.callRoute("/user/42/orders/99"));
        // "User 42 Order 99"
    }
}
