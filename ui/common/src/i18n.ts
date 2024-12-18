type I18nKey = string;
type I18nDict = Record<I18nKey, string>;
type Trans = any;

export const trans = (i18n: I18nDict): Trans => {
  const trans: Trans = (key: I18nKey, ...args: Array<string | number>) => {
    const str = i18n[key];
    return str ? format(str, args) : key;
  };

  // see optimisations in project/MessageCompiler.scala
  const resolvePlural = (key: I18nKey, count: number) =>
    i18n[`${key}:${site.quantity(count)}`] || i18n[`${key}:other`] || i18n[key] || i18n[`${key}:one`];

  trans.pluralSame = (key: I18nKey, count: number, ...args: Array<string | number>) =>
    trans.plural(key, count, count, ...args);

  trans.plural = function (key: I18nKey, count: number, ...args: Array<string | number>) {
    const str = resolvePlural(key, count);
    return str ? format(str, args) : key;
  };
  // optimisation for translations without arguments
  trans.noarg = (key: I18nKey) => i18n[key] || key;
  trans.vdom = <T>(key: I18nKey, ...args: T[]) => {
    const str = i18n[key];
    return str ? list(str, args) : [key];
  };
  trans.vdomPlural = <T>(key: I18nKey, count: number, ...args: T[]) => {
    const str = resolvePlural(key, count);
    return str ? list(str, args) : [key];
  };
  return trans;
};

// for many users, using the islamic calendar is not practical on the internet
// due to international context, so we make sure it's displayed using the gregorian calendar
export const displayLocale: string = document.documentElement.lang.startsWith('ar-')
  ? 'ar-ly'
  : document.documentElement.lang;

const commonDateFormatter = new Intl.DateTimeFormat(displayLocale, {
  year: 'numeric',
  month: 'short',
  day: 'numeric',
  hour: 'numeric',
  minute: 'numeric',
});

export const commonDateFormat: (d?: Date | number) => string = commonDateFormatter.format;

export const timeago: (d: DateLike) => string = (date: DateLike) =>
  formatAgo((Date.now() - toDate(date).getTime()) / 1000);

// format Date / string / timestamp to Date instance.
export const toDate = (input: DateLike): Date =>
  input instanceof Date ? input : new Date(isNaN(input as any) ? input : parseInt(input as any));

export const use24h = (): boolean => !commonDateFormatter.resolvedOptions().hour12;

// format the diff second to *** time ago
export const formatAgo = (seconds: number): string => {
  const absSeconds = Math.abs(seconds);
  const strIndex = seconds < 0 ? 1 : 0;
  const unit = agoUnits.find(unit => absSeconds >= unit[2] * unit[3] && unit[strIndex])!;
  const fmt = i18n.timeago[unit[strIndex]!];
  return typeof fmt === 'string' ? fmt : fmt(Math.floor(absSeconds / unit[2]));
};

type DateLike = Date | number | string;

// past, future, divisor, at least
const agoUnits: [keyof I18n['timeago'] | undefined, keyof I18n['timeago'], number, number][] = [
  ['nbYearsAgo', 'inNbYears', 60 * 60 * 24 * 365, 1],
  ['nbMonthsAgo', 'inNbMonths', (60 * 60 * 24 * 365) / 12, 1],
  ['nbWeeksAgo', 'inNbWeeks', 60 * 60 * 24 * 7, 1],
  ['nbDaysAgo', 'inNbDays', 60 * 60 * 24, 2],
  ['nbHoursAgo', 'inNbHours', 60 * 60, 1],
  ['nbMinutesAgo', 'inNbMinutes', 60, 1],
  [undefined, 'inNbSeconds', 1, 9],
  ['rightNow', 'justNow', 1, 0],
];

function format(str: string, args: Array<string | number>): string {
  if (args.length) {
    if (str.includes('%s')) str = str.replace('%s', args[0] as string);
    else for (let i = 0; i < args.length; i++) str = str.replace('%' + (i + 1) + '$s', args[i] as string);
  }
  return str;
}

function list<T>(str: string, args: T[]): Array<string | T> {
  const segments: Array<string | T> = str.split(/(%(?:\d\$)?s)/g);
  if (args.length) {
    const singlePlaceholder = segments.indexOf('%s');
    if (singlePlaceholder !== -1) segments[singlePlaceholder] = args[0];
    else
      for (let i = 0; i < args.length; i++) {
        const placeholder = segments.indexOf('%' + (i + 1) + '$s');
        if (placeholder !== -1) segments[placeholder] = args[i];
      }
  }
  return segments;
}
