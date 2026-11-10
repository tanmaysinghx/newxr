export interface UserRef { id: number; name: string; email: string; initials: string; color: string; }

export interface Me extends UserRef {
  role: 'ADMIN' | 'MEMBER' | 'GUEST';
  signedInWith: 'password' | 'portal';
  jobTitle?: string;
  apps: string[];
}

export interface SignInOptions {
  organization: string; logoUrl?: string; supportUrl?: string; setupRequired: boolean;
  builtinEnabled: boolean; portalEnabled: boolean; portalButtonLabel: string; ssoLoginPath?: string;
}

export interface AppInfo { code: string; name: string; short: string; tagline: string; icon: string; color: string; home: string; }

export const APPS: AppInfo[] = [
  { code: 'track', name: 'Newxr Track', short: 'Track', tagline: 'Plan, track and ship work', icon: 'board', color: 'var(--track)', home: '/track' },
  { code: 'pages', name: 'Newxr Pages', short: 'Pages', tagline: 'Docs and knowledge for every team', icon: 'page', color: 'var(--pages)', home: '/pages' },
  { code: 'boards', name: 'Newxr Boards', short: 'Boards', tagline: 'Visual boards for anything', icon: 'kanban', color: 'var(--boards)', home: '/boards' },
  { code: 'code', name: 'Newxr Code', short: 'Code', tagline: 'Git repos, pull requests, pipelines', icon: 'code', color: 'var(--code)', home: '/code' },
  { code: 'desk', name: 'Newxr Desk', short: 'Desk', tagline: 'Service desk and help center', icon: 'headset', color: 'var(--desk)', home: '/desk' },
  { code: 'pulse', name: 'Newxr Pulse', short: 'Pulse', tagline: 'Alerts, on-call and status pages', icon: 'bolt', color: 'var(--pulse)', home: '/pulse' },
  { code: 'goals', name: 'Newxr Goals', short: 'Goals', tagline: 'Goals and project updates', icon: 'target', color: 'var(--goals)', home: '/goals' },
];

export const appInfo = (code: string) => APPS.find(a => a.code === code);
