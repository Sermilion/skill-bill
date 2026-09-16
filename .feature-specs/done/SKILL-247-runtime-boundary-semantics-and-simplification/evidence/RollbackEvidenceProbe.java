import java.lang.reflect.*;
import java.nio.file.Path;
import java.sql.*;
import kotlin.jvm.functions.Function0;

public class RollbackEvidenceProbe {
  public static void main(String[] args) throws Exception {
    boolean[] rollbackAttempted = {false};
    Statement statement = (Statement) Proxy.newProxyInstance(RollbackEvidenceProbe.class.getClassLoader(), new Class<?>[]{Statement.class}, (p,m,a) -> {
      if (m.getName().equals("execute")) {
        if (a[0].equals("ROLLBACK")) { rollbackAttempted[0] = true; throw new SQLException("rollback-probe-failure"); }
        return true;
      }
      return null;
    });
    Connection connection = (Connection) Proxy.newProxyInstance(RollbackEvidenceProbe.class.getClassLoader(), new Class<?>[]{Connection.class}, (p,m,a) -> statement);
    Class<?> owner = Class.forName("skillbill.infrastructure.sqlite.SQLiteDatabaseSessionFactoryKt");
    Method method = owner.getDeclaredMethod("inTransaction", Connection.class, Path.class, Function0.class);
    method.setAccessible(true);
    try {
      method.invoke(null, connection, Path.of("/tmp/unused-probe.db"), (Function0<Object>) () -> { throw new IllegalStateException("primary-probe-failure"); });
    } catch (InvocationTargetException error) {
      System.out.println("primary_failure=" + error.getCause().getMessage());
      System.out.println("rollback_attempted=" + rollbackAttempted[0]);
      System.out.println("suppressed_cleanup_failures=" + error.getCause().getSuppressed().length);
    }
  }
}
