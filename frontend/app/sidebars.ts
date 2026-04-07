import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  introduction: [
    'Introduction/Why-This-Project',
  ],

  bootstrap: [
    'Bootstrap/Overview',
    'Bootstrap/Maven-Modules',
    'Bootstrap/Docker-Compose',
    'Bootstrap/JPA-Entities',
    'Bootstrap/API-Gateway',
    'Bootstrap/Tests',
  ],

  reference: [
    'Reference/System-Architecture',
    'Reference/Learning-Notes',
    'Reference/Spec',
    'Reference/Implementation-Plan',
  ],
};

export default sidebars;
