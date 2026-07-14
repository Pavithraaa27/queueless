import { useEffect, useState } from 'react';
import { Box, Button, Stack, Typography } from '@mui/material';
import Grid from '@mui/material/Grid2';
import { Link } from 'react-router-dom';
import { tokens } from '../theme/theme';

const DEMO_ROWS = [
  { ticket: '014', name: 'A. Rao', status: 'NOW SERVING' },
  { ticket: '015', name: 'S. Iyer', eta: '4 min' },
  { ticket: '016', name: 'M. Khan', eta: '9 min' },
  { ticket: '017', name: 'P. Nair', eta: '13 min' },
];

export default function Landing() {
  const [tick, setTick] = useState(0);
  useEffect(() => {
    const id = setInterval(() => setTick((t) => t + 1), 1800);
    return () => clearInterval(id);
  }, []);

  return (
    <Stack spacing={8}>
      <Grid container spacing={5} alignItems="center">
        <Grid size={{ xs: 12, md: 7 }}>
          <Typography variant="overline" sx={{ color: tokens.amber }}>
            No more standing in line
          </Typography>
          <Typography variant="h1" sx={{ fontSize: { xs: 34, md: 44 }, mt: 1, mb: 2 }}>
            Join the queue from<br />wherever you are.
          </Typography>
          <Typography sx={{ color: tokens.inkMuted, fontSize: 17, maxWidth: 460, mb: 4 }}>
            Check in to a clinic, salon, or office remotely. Watch your position update live,
            and get a wait estimate that adjusts as the line actually moves — not a static guess.
          </Typography>
          <Stack direction="row" spacing={2}>
            <Button variant="contained" size="large" component={Link} to="/businesses">
              Find a queue
            </Button>
            <Button variant="outlined" size="large" component={Link} to="/register" sx={{ borderColor: tokens.hairline, color: tokens.ink }}>
              Run a business
            </Button>
          </Stack>
        </Grid>

        <Grid size={{ xs: 12, md: 5 }}>
          <Box
            sx={{
              backgroundColor: tokens.surface,
              border: `1px solid ${tokens.hairline}`,
              borderRadius: 3,
              p: 2.5,
            }}
          >
            <Stack direction="row" justifyContent="space-between" sx={{ mb: 1.5 }}>
              <Typography variant="overline" sx={{ color: tokens.inkMuted }}>
                Downtown Clinic
              </Typography>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.7 }}>
                <Box sx={{ width: 6, height: 6, borderRadius: '50%', backgroundColor: tokens.mint }} />
                <Typography variant="overline" sx={{ color: tokens.mint, fontSize: 10 }}>
                  live
                </Typography>
              </Box>
            </Stack>
            <Stack spacing={1}>
              {DEMO_ROWS.map((row, i) => (
                <Box
                  key={row.ticket}
                  sx={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    px: 1.5,
                    py: 1,
                    borderRadius: 1.5,
                    backgroundColor: i === 0 ? `${tokens.mint}14` : 'transparent',
                    opacity: i === 0 ? 1 : 1 - i * 0.08 + (tick % 2 === 0 ? 0.02 : 0),
                  }}
                >
                  <Stack direction="row" spacing={1.5} alignItems="center">
                    <Typography className="tabular-nums" sx={{ color: i === 0 ? tokens.mint : tokens.amber, fontWeight: 700 }}>
                      {row.ticket}
                    </Typography>
                    <Typography variant="body2" sx={{ color: tokens.ink }}>
                      {row.name}
                    </Typography>
                  </Stack>
                  <Typography variant="body2" className="tabular-nums" sx={{ color: i === 0 ? tokens.mint : tokens.inkMuted, fontWeight: i === 0 ? 700 : 400 }}>
                    {row.status ?? row.eta}
                  </Typography>
                </Box>
              ))}
            </Stack>
          </Box>
        </Grid>
      </Grid>

      <Grid container spacing={4}>
        {[
          { label: 'Check in remotely', body: 'Tap in from your phone the moment you\'d normally walk through the door.' },
          { label: 'Watch it live', body: 'Your position and wait estimate update the instant the queue moves.' },
          { label: 'Wait estimates that learn', body: 'Each business\'s average service time adjusts itself from real, recent visits.' },
        ].map((item) => (
          <Grid size={{ xs: 12, md: 4 }} key={item.label}>
            <Typography sx={{ fontWeight: 700, mb: 1 }}>{item.label}</Typography>
            <Typography variant="body2" sx={{ color: tokens.inkMuted }}>
              {item.body}
            </Typography>
          </Grid>
        ))}
      </Grid>
    </Stack>
  );
}
