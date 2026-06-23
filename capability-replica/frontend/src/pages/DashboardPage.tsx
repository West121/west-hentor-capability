import { useEffect, useMemo, useState } from 'react';
import type { CSSProperties, MouseEvent, ReactNode } from 'react';
import { api } from '../services/api';
import type { DashboardStatistics, NameValue, OrgCount } from '../types/domain';

type DashboardCard = {
  value: number;
  label: string;
  color: string;
};

type DonutRow = {
  orgName: string;
  count: number;
  color: string;
};

type DonutActiveState = {
  orgName: string;
  x: number;
  y: number;
};

const demoOrgRows: DonutRow[] = [
  { orgName: 'NF', count: 1866, color: '#5b8ff9' },
  { orgName: 'EMS', count: 1221, color: '#5ad8a6' },
  { orgName: 'General & XRD', count: 229, color: '#5d7092' },
  { orgName: 'SIR', count: 3340, color: '#f6bd16' },
  { orgName: 'Lab Group', count: 235, color: '#e8684a' },
  { orgName: 'OGC', count: 499, color: '#6dc8ec' },
  { orgName: 'CHEM', count: 5023, color: '#9270ca' },
];

const demoWeekRows: NameValue[] = [
  { name: '2026-06-11', value: 3 },
  { name: '2026-06-16', value: 7 },
];

const dashboardOrgOrder = ['NF', 'EMS', 'General & XRD', 'SIR', 'Lab Group', 'OGC', 'CHEM'];
const donutColorByOrgName: Record<string, string> = {
  nf: '#5b8ff9',
  ems: '#5ad8a6',
  'general & xrd': '#5d7092',
  sir: '#f6bd16',
  'lab group': '#e8684a',
  ogc: '#6dc8ec',
  chem: '#9270ca',
};

// Workbench mirrors the production SGS dashboard cards and two chart panels.
export default function DashboardPage() {
  const [stats, setStats] = useState<DashboardStatistics>();
  const [orgRows, setOrgRows] = useState<OrgCount[]>([]);
  const [weekRows, setWeekRows] = useState<NameValue[]>([]);

  useEffect(() => {
    void Promise.all([api.dashboardStatistics(), api.orgCount(), api.changeCountInWeek()]).then(
      ([statistics, orgCount, week]) => {
        setStats(statistics);
        setOrgRows(orgCount.items);
        setWeekRows(week.items);
      },
    );
  }, []);

  const view = useMemo(() => {
    const useDemoData = !stats || stats.abilityCount < 100;
    const donutRows = normalizeDonutRows(useDemoData ? demoOrgRows : orgRows);
    const total = useDemoData ? 12413 : stats.abilityCount;
    const chartRows = useDemoData ? demoWeekRows : weekRows.filter((row) => Number(row.value) > 0);
    const cards: DashboardCard[] = [
      { value: total, label: '总检测项目数', color: '#1e9af0' },
      { value: useDemoData ? 4 : stats.changeCountInWeek, label: '一周内新增', color: '#49d116' },
      { value: useDemoData ? 18 : stats.changeCountInMonth ?? stats.changeCountInWeek, label: '一月内新增', color: '#ff9416' },
      { value: useDemoData ? 0 : stats.deleteCountInWeek ?? 0, label: '一周内删除', color: '#e82f9b' },
    ];

    return { total, cards, donutRows, chartRows };
  }, [orgRows, stats, weekRows]);

  return (
    <div className="dashboard-workbench">
      <h1>NR实验室检测能力查询系统</h1>
      <section className="dashboard-summary-grid" aria-label="工作台统计">
        {view.cards.map((card) => (
          <div className="dashboard-summary-card" style={{ backgroundColor: card.color }} key={card.label}>
            <strong>{card.value}</strong>
            <span>{card.label}</span>
          </div>
        ))}
      </section>
      <section className="dashboard-chart-grid">
        <DashboardPanel title="一周内变化数量">
          <WeeklyChangeChart rows={view.chartRows} />
        </DashboardPanel>
        <DashboardPanel title="能力表数量">
          <AbilityDonutChart rows={view.donutRows} total={view.total} />
        </DashboardPanel>
      </section>
    </div>
  );
}

function DashboardPanel({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="dashboard-panel">
      <div className="dashboard-panel-title">{title}</div>
      <div className="dashboard-panel-body">{children}</div>
    </div>
  );
}

function WeeklyChangeChart({ rows }: { rows: NameValue[] }) {
  const visibleRows = rows.length ? rows : [{ name: '-', value: 0 }];
  const maxValue = Math.max(7, ...visibleRows.map((row) => Number(row.value)));

  return (
    <div className="dashboard-bar-chart" aria-label="一周内变化数量柱状图">
      <div className="dashboard-y-axis">
        {[6, 4, 2, 0].map((value) => (
          <span key={value}>{value}</span>
        ))}
      </div>
      <div className="dashboard-bar-plot">
        {[6, 4, 2, 0].map((value) => (
          <span className="dashboard-gridline" style={{ bottom: `${(value / 7) * 100}%` }} key={value} />
        ))}
        <div className="dashboard-bars">
          {visibleRows.map((row) => {
            const value = Number(row.value);
            const barHeight = `${(value / maxValue) * 100}%`;
            const barStyle = { '--bar-height': barHeight } as CSSProperties;

            return (
              <div className="dashboard-bar-item" key={row.name}>
                <div className="dashboard-bar-column" style={barStyle} tabIndex={0} aria-label={`${row.name}: ${row.value}`}>
                  <span className="dashboard-bar-tooltip">{`${row.name}: ${row.value}`}</span>
                  <span className="dashboard-bar-value">{row.value}</span>
                  <div className="dashboard-bar" />
                </div>
                <span>{row.name}</span>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

function AbilityDonutChart({ rows, total }: { rows: DonutRow[]; total: number }) {
  const radius = 82;
  const strokeWidth = 24;
  const [active, setActive] = useState<DonutActiveState>();
  const [hiddenOrgs, setHiddenOrgs] = useState<string[]>([]);
  const visibleRows = rows.filter((row) => !hiddenOrgs.includes(row.orgName));
  const visibleTotal = visibleRows.reduce((sum, row) => sum + row.count, 0);
  const activeRow = rows.find((row) => row.orgName === active?.orgName);
  const tooltipStyle = active ? ({ left: active.x, top: active.y } as CSSProperties) : undefined;
  const segments = buildDonutSegments(visibleRows, visibleTotal, radius);

  function percent(row: DonutRow) {
    return total ? (row.count / total) * 100 : 0;
  }

  function pointerPosition(event: MouseEvent<SVGPathElement>) {
    const container = event.currentTarget.closest('.dashboard-donut');
    const rect = container?.getBoundingClientRect();
    if (!rect) {
      return fallbackPosition(event.currentTarget.getAttribute('aria-label')?.split(':')[0] ?? '');
    }
    return {
      x: clamp(event.clientX - rect.left, 76, rect.width - 76),
      y: clamp(event.clientY - rect.top, 62, rect.height - 14),
    };
  }

  function fallbackPosition(orgName: string) {
    let startRatio = 0;
    const row = visibleRows.find((item) => {
      if (item.orgName === orgName) {
        return true;
      }
      startRatio += visibleTotal ? item.count / visibleTotal : 0;
      return false;
    });
    const midRatio = row ? startRatio + (visibleTotal ? row.count / visibleTotal : 0) / 2 : 0.25;
    const angle = midRatio * Math.PI * 2 - Math.PI / 2;
    return {
      x: clamp(150 + Math.cos(angle) * 92, 76, 224),
      y: clamp(150 + Math.sin(angle) * 92, 62, 286),
    };
  }

  function activateFromSegment(row: DonutRow, event: MouseEvent<SVGPathElement>) {
    setActive({ orgName: row.orgName, ...pointerPosition(event) });
  }

  function activateFromLegend(row: DonutRow) {
    if (hiddenOrgs.includes(row.orgName)) {
      setActive(undefined);
      return;
    }
    setActive({ orgName: row.orgName, ...fallbackPosition(row.orgName) });
  }

  function toggleLegend(row: DonutRow) {
    setActive(undefined);
    setHiddenOrgs((current) =>
      current.includes(row.orgName) ? current.filter((name) => name !== row.orgName) : [...current, row.orgName],
    );
  }

  return (
    <div className="dashboard-donut-wrap">
      <div className="dashboard-donut">
        <svg viewBox="0 0 220 220" role="img" aria-label="能力表数量环形图">
          <circle cx="110" cy="110" r={radius} fill="none" stroke="#f3f5f8" strokeWidth={strokeWidth} />
          {segments.map((segment) => {
            const isActive = segment.orgName === active?.orgName;
            const isDimmed = Boolean(active) && !isActive;
            return (
              <path
                className={`dashboard-donut-segment${isActive ? ' dashboard-donut-segment-active' : ''}${
                  isDimmed ? ' dashboard-donut-segment-dimmed' : ''
                }`}
                d={segment.path}
                fill="none"
                stroke={segment.color}
                strokeWidth={strokeWidth}
                tabIndex={0}
                role="button"
                aria-label={`${segment.orgName}: ${segment.count}, ${percent(segment).toFixed(2)}%`}
                onMouseEnter={(event) => activateFromSegment(segment, event)}
                onMouseMove={(event) => activateFromSegment(segment, event)}
                onMouseLeave={() => setActive(undefined)}
                onFocus={() => activateFromLegend(segment)}
                onBlur={() => setActive(undefined)}
                key={segment.orgName}
              />
            );
          })}
        </svg>
        <strong>{total}</strong>
        {activeRow ? (
          <div className="dashboard-donut-tooltip" style={tooltipStyle}>
            <span className="dashboard-donut-tooltip-dot" style={{ backgroundColor: activeRow.color }} />
            <span className="dashboard-donut-tooltip-name">{activeRow.orgName}:</span>
            <span className="dashboard-donut-tooltip-percent">{`${percent(activeRow).toFixed(2)} %`}</span>
          </div>
        ) : null}
      </div>
      <div className="dashboard-donut-legend">
        {rows.map((row) => {
          const hidden = hiddenOrgs.includes(row.orgName);
          const activeLegend = !hidden && row.orgName === active?.orgName;

          return (
            <button
              className={`dashboard-donut-legend-row${activeLegend ? ' dashboard-donut-legend-row-active' : ''}${
                hidden ? ' dashboard-donut-legend-row-hidden' : ''
              }`}
              key={row.orgName}
              type="button"
              aria-pressed={!hidden}
              onClick={() => toggleLegend(row)}
              onMouseEnter={() => activateFromLegend(row)}
              onMouseLeave={() => setActive(undefined)}
              onFocus={() => activateFromLegend(row)}
              onBlur={() => setActive(undefined)}
            >
              <span className="dashboard-donut-dot" style={{ backgroundColor: row.color }} />
              <span className="dashboard-donut-name">{row.orgName}</span>
              <span className="dashboard-donut-divider">|</span>
              <span className="dashboard-donut-percent">{`${percent(row).toFixed(2)}%`}</span>
              <span className="dashboard-donut-count">{row.count}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
}

function normalizeDonutRows(rows: Array<{ orgName: string; count: number; color?: string }>): DonutRow[] {
  return rows
    .map((row) => ({
      orgName: row.orgName,
      count: row.count,
      color: donutColorByOrgName[normalizeOrgName(row.orgName)] ?? row.color ?? '#9aa5b1',
    }))
    .sort((left, right) => orgOrderIndex(left.orgName) - orgOrderIndex(right.orgName));
}

function orgOrderIndex(name: string) {
  const index = dashboardOrgOrder.findIndex((item) => normalizeOrgName(item) === normalizeOrgName(name));
  return index === -1 ? dashboardOrgOrder.length : index;
}

function normalizeOrgName(name: string) {
  return name.trim().toLowerCase();
}

function buildDonutSegments(rows: DonutRow[], total: number, radius: number) {
  let startRatio = 0;
  const gapAngle = 0.022;
  return rows.map((row) => {
    const ratio = total ? row.count / total : 0;
    const startAngle = startRatio * Math.PI * 2 - Math.PI / 2 + gapAngle;
    const endAngle = (startRatio + ratio) * Math.PI * 2 - Math.PI / 2 - gapAngle;
    const safeEndAngle = endAngle <= startAngle ? startAngle + 0.002 : endAngle;
    startRatio += ratio;
    return {
      ...row,
      path: describeArc(110, 110, radius, startAngle, safeEndAngle),
    };
  });
}

function describeArc(cx: number, cy: number, radius: number, startAngle: number, endAngle: number) {
  const start = polarPoint(cx, cy, radius, startAngle);
  const end = polarPoint(cx, cy, radius, endAngle);
  const largeArcFlag = endAngle - startAngle > Math.PI ? 1 : 0;
  return `M ${start.x.toFixed(3)} ${start.y.toFixed(3)} A ${radius} ${radius} 0 ${largeArcFlag} 1 ${end.x.toFixed(3)} ${end.y.toFixed(3)}`;
}

function polarPoint(cx: number, cy: number, radius: number, angle: number) {
  return {
    x: cx + radius * Math.cos(angle),
    y: cy + radius * Math.sin(angle),
  };
}
