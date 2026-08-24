public class Demo {

    public static void main(String[] args) {
        Directory root = buildTree();

        System.out.println("== One operation, every level, no isLeaf anywhere ==");
        System.out.print(root.render(0));

        System.out.println();
        System.out.println("== The client asks any node the same question ==");
        for (Node child : root.children()) {
            System.out.println("  " + child.name() + " -> "
                    + FileNode.humanBytes(child.sizeBytes()));
        }
        System.out.println("  The loop above does not know or care which of those are");
        System.out.println("  directories. That is the whole pattern.");

        System.out.println();
        System.out.println("== Search, defined once and inherited by the shape of the tree ==");
        System.out.println("  java files:");
        root.find(node -> node.name().endsWith(".java"))
            .forEach(node -> System.out.println("    " + node.name()));
        System.out.println("  anything over 100K:");
        root.find(node -> node.sizeBytes() > 100 * 1024)
            .forEach(node -> System.out.println("    " + node.name()
                    + " " + FileNode.humanBytes(node.sizeBytes())));

        System.out.println();
        System.out.println("  total files: " + root.countFiles()
                + "   total size: " + FileNode.humanBytes(root.sizeBytes()));

        System.out.println();
        System.out.println("== Recursive delete is one line, because the tree does the work ==");
        Directory src = (Directory) root.find(n -> n.name().equals("src")).get(0);
        System.out.println("  removing src/main (" + FileNode.humanBytes(
                src.find(n -> n.name().equals("main")).get(0).sizeBytes()) + ")");
        src.remove("main");
        System.out.println("  total size now: " + FileNode.humanBytes(root.sizeBytes()));
        System.out.print(root.render(0));

        System.out.println();
        System.out.println("== The trap: a cycle ==");
        Directory loop = new Directory("loop");
        loop.add(new FileNode("real.txt", 1024));
        loop.add(loop);                              // nothing in the pattern forbids this
        System.out.println("  sizeBytes() would recurse until the stack goes.");
        System.out.println("  safeSizeBytes() with a visited set: "
                + FileNode.humanBytes(loop.safeSizeBytes()));
        System.out.println("  Real file systems have hard links, so this is not hypothetical.");
        System.out.println("  Mention it in the interview; implement it only if asked.");
    }

    private static Directory buildTree() {
        return new Directory("project")
                .add(new Directory("src")
                        .add(new Directory("main")
                                .add(new FileNode("Order.java", 4_200))
                                .add(new FileNode("Payment.java", 6_800))
                                .add(new Directory("risk")
                                        .add(new FileNode("RiskCheck.java", 3_100))
                                        .add(new FileNode("VelocityCheck.java", 2_400))))
                        .add(new Directory("test")
                                .add(new FileNode("OrderTest.java", 5_600))))
                .add(new Directory("build")
                        .add(new FileNode("app.jar", 340 * 1024)))
                .add(new FileNode("README.md", 1_800))
                .add(new FileNode("pom.xml", 900));
    }
}
