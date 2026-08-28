const BRAZILIAN_DATE_DIGITS = 8;
const ISO_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

export type DateOnlyParts = {
  year: number;
  month: number;
  day: number;
};

export function applyBrazilianDateMask(value: string) {
  const digits = value.replace(/\D/g, '').slice(0, BRAZILIAN_DATE_DIGITS);
  const day = digits.slice(0, 2);
  const month = digits.slice(2, 4);
  const year = digits.slice(4, 8);

  return [day, month, year].filter(Boolean).join('/');
}

function isValidDateParts(day: number, month: number, year: number) {
  if (year < 1 || month < 1 || month > 12 || day < 1) {
    return false;
  }

  const date = new Date(year, month - 1, day);
  return date.getFullYear() === year
    && date.getMonth() === month - 1
    && date.getDate() === day;
}

export function parseBrazilianDate(value: string, options: { allowFuture?: boolean } = {}) {
  const digits = value.replace(/\D/g, '');
  if (digits.length !== BRAZILIAN_DATE_DIGITS) {
    return null;
  }

  const day = Number(digits.slice(0, 2));
  const month = Number(digits.slice(2, 4));
  const year = Number(digits.slice(4, 8));

  if (!isValidDateParts(day, month, year)) {
    return null;
  }

  const date = new Date(year, month - 1, day);
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  if (!options.allowFuture && date > today) {
    return null;
  }

  return { day, month, year };
}

export function formatDateForApi(value: string, options?: { allowFuture?: boolean }) {
  const parsed = parseBrazilianDate(value, options);
  if (!parsed) {
    return null;
  }

  const day = String(parsed.day).padStart(2, '0');
  const month = String(parsed.month).padStart(2, '0');
  const year = String(parsed.year).padStart(4, '0');
  return `${year}-${month}-${day}`;
}

export function formatDateForDisplay(value?: string | null) {
  if (!value) {
    return '';
  }

  const isoDate = ISO_DATE_PATTERN.test(value) ? value : value.slice(0, 10);
  if (!ISO_DATE_PATTERN.test(isoDate)) {
    return '';
  }

  const [year, month, day] = isoDate.split('-');
  return `${day}/${month}/${year}`;
}

export function parseDateOnly(value?: string | null): DateOnlyParts | null {
  if (!value) {
    return null;
  }

  const isoDate = ISO_DATE_PATTERN.test(value) ? value : value.slice(0, 10);
  if (!ISO_DATE_PATTERN.test(isoDate)) {
    return null;
  }

  const [yearValue, monthValue, dayValue] = isoDate.split('-').map(Number);
  if (!isValidDateParts(dayValue, monthValue, yearValue)) {
    return null;
  }

  return { year: yearValue, month: monthValue, day: dayValue };
}

export function formatDateOnlyPtBr(value?: string | null, options: { long?: boolean } = {}) {
  const parsed = parseDateOnly(value);
  if (!parsed) {
    return '';
  }

  const date = new Date(parsed.year, parsed.month - 1, parsed.day);
  if (options.long) {
    return new Intl.DateTimeFormat('pt-BR', { day: 'numeric', month: 'long', year: 'numeric' }).format(date);
  }

  return new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'short' }).format(date).toUpperCase();
}

export function formatLongDatePtBr(value?: string | null) {
  return formatDateOnlyPtBr(value, { long: true });
}

export function dateOnlyFromLocalDate(date: Date) {
  const year = String(date.getFullYear()).padStart(4, '0');
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}
