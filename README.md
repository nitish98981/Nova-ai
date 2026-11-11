# NOVA AI — Phase 1 (Foundation)

Futuristic home screen, Settings aur Permissions dashboard. Voice, AI brain aur phone control aage ke phases mein aayenge.

## Phone se APK banane ke steps (Termux + GitHub)

1. GitHub par naya repo banao (naam: `nova-ai`). Settings → Developer settings → Personal access tokens se ek token banao (repo permission ke saath).
2. Termux mein:
   ```
   pkg update
   pkg install git unzip
   unzip nova-ai.zip
   cd nova-ai
   git init
   git config user.name "TumharaNaam"
   git config user.email "tumhara@email.com"
   git add .
   git commit -m "NOVA AI phase 1"
   git branch -M main
   git remote add origin https://github.com/USERNAME/nova-ai.git
   git push -u origin main
   ```
   Password maangne par token paste karo.
3. GitHub repo → **Actions** tab → "Build APK" chalega (5-10 min).
4. Build poora hone par run kholo → neeche **Artifacts** → `nova-debug-apk` download karo → zip khol kar `app-debug.apk` install karo.

Agar build fail ho to Actions mein log ka error copy karke mujhe bhej dena.
