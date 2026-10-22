import type { Metadata } from "next";
import { AuthProvider } from "../contexts/AuthContext";
import { AuthGate } from "../contexts/AuthGate";
import "./globals.css";

export const metadata: Metadata = {
  title: "demo refresh token",
  description: "demo auth LDAP + refresh token",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="ja"
      className={"h-full antialiased"}
    >
      <body className="min-h-full flex flex-col">
        <AuthProvider><AuthGate>{children}</AuthGate></AuthProvider>
      </body>
    </html>
  );
}
