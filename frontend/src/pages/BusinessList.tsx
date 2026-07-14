import { useEffect, useState } from 'react';
import { Alert, Box, Button, Chip, Paper, Stack, Typography } from '@mui/material';
import { Link } from 'react-router-dom';
import { listBusinesses, getNearbyBusinesses } from '../api/business';
import { useGeolocation } from '../hooks/useGeolocation';
import { tokens } from '../theme/theme';
import type { Business } from '../types';

export default function BusinessList() {
  const [businesses, setBusinesses] = useState<Business[]>([]);
  const [loading, setLoading] = useState(true);
  const [nearMeActive, setNearMeActive] = useState(false);
  const { coords, status, request } = useGeolocation();

  useEffect(() => {
    listBusinesses()
      .then(setBusinesses)
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (coords && nearMeActive) {
      setLoading(true);
      getNearbyBusinesses(coords.lat, coords.lng, 15)
        .then(setBusinesses)
        .finally(() => setLoading(false));
    }
  }, [coords, nearMeActive]);

  function handleNearMe() {
    setNearMeActive(true);
    request();
  }

  function handleShowAll() {
    setNearMeActive(false);
    setLoading(true);
    listBusinesses().then(setBusinesses).finally(() => setLoading(false));
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h3" sx={{ fontSize: 28, mb: 0.5 }}>
          Find a queue
        </Typography>
        <Typography variant="body2" sx={{ color: tokens.inkMuted }}>
          Check in remotely and track your position live.
        </Typography>
      </Box>

      <Stack direction="row" spacing={1.5} alignItems="center">
        <Button
          size="small"
          variant={nearMeActive ? 'contained' : 'outlined'}
          onClick={handleNearMe}
          sx={!nearMeActive ? { borderColor: tokens.hairline, color: tokens.ink } : undefined}
        >
          {status === 'locating' ? 'Locating…' : 'Near me'}
        </Button>
        {nearMeActive && (
          <Button size="small" onClick={handleShowAll} sx={{ color: tokens.inkMuted }}>
            Show all
          </Button>
        )}
      </Stack>

      {status === 'denied' && (
        <Alert severity="warning">
          Location access was denied, so we can't sort by distance — showing all businesses instead.
        </Alert>
      )}
      {status === 'unsupported' && (
        <Alert severity="warning">Your browser doesn't support location — showing all businesses instead.</Alert>
      )}

      {loading && <Typography sx={{ color: tokens.inkMuted }}>Loading…</Typography>}

      {!loading && businesses.length === 0 && (
        <Paper sx={{ p: 4, textAlign: 'center', backgroundColor: tokens.surface, border: `1px solid ${tokens.hairline}` }}>
          <Typography sx={{ color: tokens.inkMuted }}>
            {nearMeActive ? 'No businesses found nearby.' : 'No businesses have registered yet. Check back soon.'}
          </Typography>
        </Paper>
      )}

      <Stack spacing={1.5}>
        {businesses.map((b) => (
          <Paper
            key={b.id}
            component={Link}
            to={`/businesses/${b.id}`}
            sx={{
              p: 2.5,
              textDecoration: 'none',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              backgroundColor: tokens.surface,
              border: `1px solid ${tokens.hairline}`,
              transition: 'border-color 0.15s ease',
              '&:hover': { borderColor: tokens.amber },
            }}
          >
            <Box>
              <Typography sx={{ fontWeight: 700, color: tokens.ink }}>{b.name}</Typography>
              <Typography variant="body2" sx={{ color: tokens.inkMuted }}>
                {b.address}
                {b.distanceKm !== null && b.distanceKm !== undefined && (
                  <Box component="span" sx={{ color: tokens.amber }}> · {b.distanceKm.toFixed(1)} km away</Box>
                )}
              </Typography>
            </Box>
            <Stack direction="row" spacing={1.5} alignItems="center">
              <Chip label={b.category} size="small" sx={{ backgroundColor: `${tokens.amber}22`, color: tokens.amber }} />
              <Box sx={{ textAlign: 'right' }}>
                <Typography className="tabular-nums" sx={{ fontWeight: 700 }}>
                  {b.currentQueueLength}
                </Typography>
                <Typography variant="overline" sx={{ color: tokens.inkMuted, fontSize: 9 }}>
                  waiting
                </Typography>
              </Box>
            </Stack>
          </Paper>
        ))}
      </Stack>
    </Stack>
  );
}
