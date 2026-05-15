import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
public class Tmp {
  public static void main(String[] args) {
    BCryptPasswordEncoder e = new BCryptPasswordEncoder();
    System.out.println("User01=" + e.encode("User01"));
    System.out.println("User02=" + e.encode("User02"));
    System.out.println("User03=" + e.encode("User03"));
  }
}
