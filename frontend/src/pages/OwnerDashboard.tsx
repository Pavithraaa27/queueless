import { useEffect, useState } from 'react';
import {
  Alert, Box, Button, MenuItem, Paper, Stack, Switch, TextField, Typography,
} from '@mui/material';
import { myBusinesses, createBusiness, setAcceptingCheckIns, getAnalytics, getInsight } from '../api/business';
import type { BusinessAnalytics } from '../api/business';
import { callNext } from '../api/queue';
import { useLiveQueue } from '../hooks/useLiveQueue';
import { useGeolocation } from '../hooks/useGeolocation';
import { tokens } from '../theme/theme';
import type { Business } from '../types';
import TicketCard from '../components/TicketCard';

const CATEGORIES = ['CLINIC', 'SALON', 'GOVT_OFFICE', 'REPAIR_SHOP', 'OTHER'];

export default function OwnerDashboard() {
  const [businesses, setBusinesses] = useState<Business[]>([]);
  const [selected, setSelected] = useState<Business | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [calling, setCalling] = useState(false);
  const [analytics, setAnalytics] = useState<BusinessAnalytics | null>(null);
  const [insight, setInsight] = useState<string | null>(null);

  const { tickets } = useLiveQueue(selected?.id ?? null);

  function refresh() {
    myBusinesses().then((list) => {
      setBusinesses(list);
      setShowCreate(list.length === 0);
      if (list.length > 0 && !selected) setSelected(list[0]);
    });
  }

  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!selected) {
      setAnalytics(null);
      setInsight(null);
      return;
    }
    getAnalytics(selected.id).then(setAnalytics).catch(() => setAnalytics(null));
    getInsight(selected.id).then(setInsight).catch(() => setInsight(null));
    // refetch analytics periodically so the panel stays current during a demo
    const id = setInterval(() => {
      getAnalytics(selected.id).then(setAnalytics).catch(() => {});
    }, 15000);
    return () => clearInterval(id);
  }, [selected?.id]);

  async function handleCallNext() {
    if (!selected) return;
    setCalling(true);
    setError(null);
    try {
      await callNext(selected.id);
    } catch (err: any) {
      setError(err?.response?.data?.error ?? 'Could not call the next customer.');
    } finally {
      setCalling(false);
    }
  }

  async function handleToggleAccepting() {
    if (!selected) return;
    const updated = await setAcceptingCheckIns(selected.id, !selected.acceptingCheckIns);
    setSelected(updated);
    setBusinesses((prev) => prev.map((b) => (b.id === updated.id ? updated : b)));
  }

  const waiting = tickets.filter((t) => t.status === 'WAITING');
  const inService = tickets.find((t) => t.status === 'IN_SERVICE');

  return (
    <Stack spacing={4}>
      <Typography variant="h3" sx={{ fontSize: 28 }}>Owner dashboard</Typography>

      {error && <Alert severity="error">{error}</Alert>}

      {businesses.length > 0 && !showCreate && (
        <TextField
          select
          label="Business"
          value={selected?.id ?? ''}
          onChange={(e) => setSelected(businesses.find((b) => b.id === Number(e.target.value)) ?? null)}
          sx={{ maxWidth: 320 }}
        >
          {businesses.map((b) => (
            <MenuItem key={b.id} value={b.id}>{b.name}</MenuItem>
          ))}
        </TextField>
      )}

      {showCreate && (
        <CreateBusinessForm
          onCreated={(b) => {
            setBusinesses((prev) => [...prev, b]);
            setSelected(b);
            setShowCreate(false);
          }}
        />
      )}

      {selected && !showCreate && (
        <>
          <Paper sx={{ p: 3, backgroundColor: tokens.surface, border: `1px solid ${tokens.hairline}` }}>
            <Stack direction="row" justifyContent="space-between" alignItems="center">
              <Box>
                <Typography sx={{ fontWeight: 700 }}>{selected.name}</Typography>
                <Typography variant="body2" sx={{ color: tokens.inkMuted }}>
                  Avg service time: ~{Math.round(selected.avgServiceTimeSeconds / 60)} min
                </Typography>
              </Box>
              <Stack direction="row" spacing={1} alignItems="center">
                <Typography variant="body2" sx={{ color: tokens.inkMuted }}>Accepting check-ins</Typography>
                <Switch checked={selected.acceptingCheckIns} onChange={handleToggleAccepting} />
              </Stack>
            </Stack>
          </Paper>

          {insight && (
            <Paper
              sx={{
                p: 2.5,
                backgroundColor: `${tokens.amber}10`,
                border: `1px solid ${tokens.amber}33`,
              }}
            >
              <Stack direction="row" spacing={1} alignItems="flex-start">
                <Typography sx={{ fontSize: 18 }}>✦</Typography>
                <Box>
                  <Typography variant="overline" sx={{ color: tokens.amber, display: 'block', mb: 0.3 }}>
                    Today's insight
                  </Typography>
                  <Typography variant="body2" sx={{ color: tokens.ink }}>
                    {insight}
                  </Typography>
                </Box>
              </Stack>
            </Paper>
          )}

          {analytics && (
            <Box>
              <Typography variant="overline" sx={{ color: tokens.inkMuted, mb: 1, display: 'block' }}>
                Today
              </Typography>
              <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap' }}>
                <StatCard label="Checked in" value={analytics.totalCheckInsToday} />
                <StatCard label="Served" value={analytics.totalServedToday} />
                <StatCard label="No-shows" value={analytics.noShowCountToday} accent={analytics.noShowCountToday > 0 ? tokens.coral : undefined} />
                <StatCard
                  label="Avg. service time today"
                  value={analytics.avgServiceTimeSecondsToday !== null
                    ? `${Math.round(analytics.avgServiceTimeSecondsToday / 60)} min`
                    : '—'}
                />
              </Box>
            </Box>
          )}

          <Box>
            <Button variant="contained" size="large" disabled={calling} onClick={handleCallNext}>
              {calling ? 'Calling…' : inService ? 'Complete & call next' : 'Call next customer'}
            </Button>
          </Box>

          <Box>
            <Typography variant="overline" sx={{ color: tokens.inkMuted }}>
              {waiting.length} waiting
            </Typography>
            <Stack spacing={1} sx={{ mt: 1.5 }}>
              {inService && <TicketCard ticket={inService} highlight />}
              {waiting.map((t) => <TicketCard key={t.id} ticket={t} />)}
              {waiting.length === 0 && !inService && (
                <Typography variant="body2" sx={{ color: tokens.inkMuted }}>Queue is empty.</Typography>
              )}
            </Stack>
          </Box>
        </>
      )}
    </Stack>
  );
}

function StatCard({ label, value, accent }: { label: string; value: string | number; accent?: string }) {
  return (
    <Paper
      sx={{
        p: 2,
        minWidth: 130,
        flex: '1 1 130px',
        backgroundColor: tokens.surface,
        border: `1px solid ${tokens.hairline}`,
      }}
    >
      <Typography className="tabular-nums" sx={{ fontSize: 24, fontWeight: 700, color: accent ?? tokens.amber }}>
        {value}
      </Typography>
      <Typography variant="body2" sx={{ color: tokens.inkMuted }}>
        {label}
      </Typography>
    </Paper>
  );
}

function CreateBusinessForm({ onCreated }: { onCreated: (b: Business) => void }) {
  const [name, setName] = useState('');
  const [category, setCategory] = useState('CLINIC');
  const [address, setAddress] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const { coords, status, request } = useGeolocation();

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const b = await createBusiness({
        name,
        category,
        address,
        latitude: coords?.lat,
        longitude: coords?.lng,
      });
      onCreated(b);
    } catch (err: any) {
      setError(err?.response?.data?.error ?? 'Could not create business.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <Paper sx={{ p: 3, backgroundColor: tokens.surface, border: `1px solid ${tokens.hairline}` }}>
      <Typography sx={{ fontWeight: 700, mb: 2 }}>Set up your business</Typography>
      <form onSubmit={handleSubmit}>
        <Stack spacing={2}>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField label="Business name" value={name} onChange={(e) => setName(e.target.value)} required fullWidth />
          <TextField select label="Category" value={category} onChange={(e) => setCategory(e.target.value)} fullWidth>
            {CATEGORIES.map((c) => <MenuItem key={c} value={c}>{c.replace('_', ' ')}</MenuItem>)}
          </TextField>
          <TextField label="Address" value={address} onChange={(e) => setAddress(e.target.value)} required fullWidth />

          <Stack direction="row" spacing={1.5} alignItems="center">
            <Button
              type="button"
              size="small"
              variant={coords ? 'contained' : 'outlined'}
              onClick={request}
              sx={!coords ? { borderColor: tokens.hairline, color: tokens.ink } : undefined}
            >
              {status === 'locating' ? 'Locating…' : coords ? 'Location set ✓' : 'Use my current location'}
            </Button>
            <Typography variant="caption" sx={{ color: tokens.inkMuted }}>
              Lets customers find you with "Near me"
            </Typography>
          </Stack>
          {status === 'denied' && (
            <Alert severity="warning">
              Location access denied — you can still create the business, but it won't show up in "Near me" searches.
            </Alert>
          )}

          <Button type="submit" variant="contained" disabled={loading}>
            {loading ? 'Creating…' : 'Create business'}
          </Button>
        </Stack>
      </form>
    </Paper>
  );
}
