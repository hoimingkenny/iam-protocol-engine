export type LessonBadge = 'Opt' | 'Ex' | 'Cap' | null;

export type Lesson = {
  id: string;
  title: string;
  href: string;
  badge: LessonBadge;
  duration: string;
  status: 'available' | 'current' | 'complete' | 'preview';
};

export type Track = {
  id: string;
  label: string;
  title: string;
  summary: string;
  href: string;
  lessons: Lesson[];
};

export const curriculum: Track[] = [
  {
    id: 'track-0',
    label: 'Track 0',
    title: 'Preparation',
    summary: 'Project framing, learning goals, and the protocol mindset needed before writing any IAM code.',
    href: '/Introduction/Why-This-Project',
    lessons: [
      {
        id: 'P0',
        title: 'Why This Project',
        href: '/Introduction/Why-This-Project',
        badge: 'Opt',
        duration: '8 min',
        status: 'current',
      },
    ],
  },
  {
    id: 'track-1',
    label: 'Track 1',
    title: 'Foundations',
    summary: 'Infrastructure, data model, and OAuth 2.0 primitives that everything else in the platform depends on.',
    href: '/Bootstrap/Overview',
    lessons: [
      {id: 'P1', title: 'Bootstrap', href: '/Bootstrap/Overview', badge: 'Ex', duration: '14 min', status: 'complete'},
      {id: 'P2', title: 'OAuth 2.0 Core', href: '/OAuth2/Overview', badge: 'Ex', duration: '18 min', status: 'complete'},
      {id: 'P3', title: 'OIDC Layer', href: '/OIDC/Overview', badge: null, duration: '16 min', status: 'complete'},
    ],
  },
  {
    id: 'track-2',
    label: 'Track 2',
    title: 'Systems',
    summary: 'Token lifecycle, admin experience, and provisioning flows that make the platform usable in enterprise scenarios.',
    href: '/Token-Lifecycle/Overview',
    lessons: [
      {id: 'P4', title: 'Token Lifecycle', href: '/Token-Lifecycle/Overview', badge: 'Ex', duration: '13 min', status: 'available'},
      {id: 'P5', title: 'Admin UI', href: '/Admin-UI/Overview', badge: 'Opt', duration: '10 min', status: 'available'},
      {id: 'P6', title: 'SCIM 2.0', href: '/SCIM/Overview', badge: 'Cap', duration: '15 min', status: 'available'},
    ],
  },
  {
    id: 'track-3',
    label: 'Track 3',
    title: 'Advanced',
    summary: 'Federation, modern authentication, and interoperability topics that raise the project from demo to IAM platform.',
    href: '/SAML/Overview',
    lessons: [
      {id: 'P7', title: 'SAML 2.0', href: '/SAML/Overview', badge: 'Ex', duration: '17 min', status: 'available'},
      {id: 'P8', title: 'Modern Auth', href: '/MFA/Overview', badge: 'Ex', duration: '19 min', status: 'available'},
      {id: 'P9', title: 'Demo Hardening', href: '/Demo-Hardening/Overview', badge: 'Cap', duration: '11 min', status: 'available'},
    ],
  },
  {
    id: 'track-4',
    label: 'Specials',
    title: 'Reference',
    summary: 'Architecture, spec context, and learning notes for deeper study and interview preparation.',
    href: '/Reference/System-Architecture',
    lessons: [
      {id: 'R1', title: 'System Architecture', href: '/Reference/System-Architecture', badge: null, duration: '12 min', status: 'preview'},
      {id: 'R2', title: 'Spec', href: '/Reference/Spec', badge: null, duration: '25 min', status: 'preview'},
      {id: 'R3', title: 'Implementation Plan', href: '/Reference/Implementation-Plan', badge: null, duration: '20 min', status: 'preview'},
    ],
  },
];

export const curriculumStats = [
  {label: 'Tracks', value: '5'},
  {label: 'Lessons', value: '13'},
  {label: 'Core focus', value: 'OAuth to SAML'},
  {label: 'Format', value: 'Docs + course'},
];
