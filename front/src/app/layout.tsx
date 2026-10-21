import type { Metadata } from "next";
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
      <body className="min-h-full flex flex-col">{children}</body>
    </html>
  );
}
