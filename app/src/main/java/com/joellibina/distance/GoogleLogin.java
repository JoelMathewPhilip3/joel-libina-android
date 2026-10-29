package com.joellibina.distance;

import android.app.Activity;
import com.google.firebase.auth.FirebaseAuth;

/** Email/password Firebase Authentication. Filename is retained for compatibility. */
public final class GoogleLogin {
  public interface Callback { void done(String message); }
  private GoogleLogin() {}
  public static boolean signedIn() {
    return FirebaseAuth.getInstance().getCurrentUser() != null;
  }
  public static void signIn(Activity activity, String email, String password, Callback callback) {
    String address = email.trim();
    if (address.isEmpty() || password.isEmpty()) { callback.done("Enter email and password."); return; }
    FirebaseAuth.getInstance().signInWithEmailAndPassword(address, password)
      .addOnSuccessListener(result -> callback.done("Signed in successfully."))
      .addOnFailureListener(error -> callback.done("Sign-in failed: " + error.getMessage()));
  }
  public static void createAccount(Activity activity, String email, String password, Callback callback) {
    String address = email.trim();
    if (address.isEmpty() || password.length() < 8) {
      callback.done("Enter an email and a password of at least 8 characters."); return;
    }
    FirebaseAuth.getInstance().createUserWithEmailAndPassword(address, password)
      .addOnSuccessListener(result -> callback.done("Account created successfully."))
      .addOnFailureListener(error -> callback.done("Account creation failed: " + error.getMessage()));
  }
  public static void resetPassword(String email, Callback callback) {
    String address = email.trim();
    if (address.isEmpty()) { callback.done("Enter your email address first."); return; }
    FirebaseAuth.getInstance().sendPasswordResetEmail(address)
      .addOnSuccessListener(ignored -> callback.done("If the email is registered, check your inbox for a reset link."))
      .addOnFailureListener(error -> callback.done("Reset request failed: " + error.getMessage()));
  }
  public static void signOut() { FirebaseAuth.getInstance().signOut(); }
}
