import type {ReactNode} from 'react';
import Link from '@docusaurus/Link';
import clsx from 'clsx';

import {
  curriculum,
  curriculumStats,
  type Lesson,
  type LessonBadge,
} from '@site/src/data/curriculum';

import styles from './styles.module.css';

function Badge({badge}: {badge: LessonBadge}) {
  if (!badge) {
    return null;
  }

  return <span className={clsx(styles.badge, styles[`badge${badge}`])}>{badge}</span>;
}

function StatusMark({lesson}: {lesson: Lesson}) {
  if (lesson.status === 'complete') {
    return <span className={styles.statusDone} aria-hidden="true">✓</span>;
  }

  if (lesson.status === 'preview') {
    return <span className={styles.statusPreview} aria-hidden="true">•</span>;
  }

  return <span className={styles.statusLive} aria-hidden="true" />;
}

export default function LearningHome(): ReactNode {
  const featuredTrack = curriculum[1];

  return (
    <div className={styles.page}>
      <section className={styles.hero}>
        <div className={styles.heroIntro}>
          <p className={styles.kicker}>Structured Developer Learning</p>
          <h1 className={styles.title}>Learn IAM protocols like a product system, not a pile of docs.</h1>
          <p className={styles.summary}>
            Move from project framing to OAuth 2.0, OIDC, SCIM, SAML, MFA, and demo hardening with a calm,
            curriculum-first reading experience.
          </p>
          <div className={styles.actions}>
            <Link className={clsx('button', styles.primaryAction)} to={featuredTrack.href}>
              Start Track 1
            </Link>
            <Link className={styles.secondaryAction} to="/Reference/System-Architecture">
              Explore architecture
            </Link>
          </div>
          <div className={styles.stats}>
            {curriculumStats.map((stat) => (
              <div key={stat.label} className={styles.statCard}>
                <span className={styles.statValue}>{stat.value}</span>
                <span className={styles.statLabel}>{stat.label}</span>
              </div>
            ))}
          </div>
        </div>

        <aside className={styles.heroRail} aria-label="Curriculum overview">
          <div className={styles.railHeader}>
            <span className={styles.railLabel}>Current path</span>
            <Link className={styles.railLink} to={featuredTrack.href}>
              View syllabus
            </Link>
          </div>
          <div className={styles.railCard}>
            {curriculum.slice(0, 4).map((track) => (
              <div key={track.id} className={styles.trackPreview}>
                <div className={styles.trackMeta}>
                  <span className={styles.trackLabel}>{track.label}</span>
                  <span className={styles.trackCount}>{track.lessons.length} lessons</span>
                </div>
                <Link className={styles.trackTitle} to={track.href}>
                  {track.title}
                </Link>
                <p className={styles.trackSummary}>{track.summary}</p>
                <div className={styles.lessonStack}>
                  {track.lessons.slice(0, 3).map((lesson) => (
                    <Link key={lesson.id} to={lesson.href} className={styles.lessonRow}>
                      <span className={styles.lessonLead}>
                        <StatusMark lesson={lesson} />
                        <span className={styles.lessonId}>{lesson.id}</span>
                      </span>
                      <span className={styles.lessonTitle}>{lesson.title}</span>
                      <Badge badge={lesson.badge} />
                    </Link>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </aside>
      </section>

      <section className={styles.section}>
        <div className={styles.sectionHeading}>
          <div>
            <p className={styles.sectionKicker}>Curriculum</p>
            <h2>Five tracks, one guided learning system</h2>
          </div>
          <p>
            Each phase is presented as a lesson sequence with stable IDs, clear progression, and dedicated
            reference material for deeper study.
          </p>
        </div>

        <div className={styles.trackGrid}>
          {curriculum.map((track) => (
            <article key={track.id} className={styles.trackCard}>
              <div className={styles.trackCardHeader}>
                <div>
                  <p className={styles.trackLabel}>{track.label}</p>
                  <h3>{track.title}</h3>
                </div>
                <span className={styles.trackCountPill}>{track.lessons.length} lessons</span>
              </div>
              <p className={styles.trackSummary}>{track.summary}</p>
              <div className={styles.trackLessons}>
                {track.lessons.map((lesson) => (
                  <Link key={lesson.id} to={lesson.href} className={styles.trackLessonRow}>
                    <div className={styles.trackLessonMeta}>
                      <StatusMark lesson={lesson} />
                      <span className={styles.lessonId}>{lesson.id}</span>
                      <span className={styles.duration}>{lesson.duration}</span>
                    </div>
                    <span className={styles.lessonTitle}>{lesson.title}</span>
                    <Badge badge={lesson.badge} />
                  </Link>
                ))}
              </div>
            </article>
          ))}
        </div>
      </section>

      <section className={styles.section}>
        <div className={styles.callout}>
          <div>
            <p className={styles.sectionKicker}>Reading model</p>
            <h2>Built to help you keep orientation while reading deeply.</h2>
            <p>
              The redesign focuses on syllabus visibility, predictable progression, and documentation-quality
              reading comfort so users always know where they are and what comes next.
            </p>
          </div>
          <div className={styles.calloutPoints}>
            <div>
              <span className={styles.calloutPointLabel}>Navigation</span>
              <p>Sticky header, sticky syllabus sidebar, lesson progression, and right-side TOC on large screens.</p>
            </div>
            <div>
              <span className={styles.calloutPointLabel}>Reading</span>
              <p>Compact metadata, restrained accent color, clean code blocks, and editorial spacing.</p>
            </div>
            <div>
              <span className={styles.calloutPointLabel}>Coverage</span>
              <p>OAuth, OIDC, token lifecycle, SCIM, SAML, MFA, and architecture references in one flow.</p>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
