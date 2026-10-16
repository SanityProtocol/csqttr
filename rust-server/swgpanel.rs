// SPDX-FileCopyrightText: 2026 SanityProtocol
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
//
// swg-panel integration knobs for csqtt. Every field defaults to the upstream/stock value, so a binary
// invoked WITHOUT any of the new flags behaves like stock csqtt. Set once at boot from the CLI args (each
// instance is its own process → a process-global OnceLock is the whole config).

use std::path::PathBuf;
use std::sync::OnceLock;

#[derive(Clone, Debug)]
pub struct InstanceCfg {
    pub iface: String,          // TUN interface name (stock: csqtt1)
    pub gw_addr: String,        // gateway/.1 address (stock: 10.66.67.1)
    pub subnet_prefix: [u8; 3], // first 3 octets of the /24 (stock: 10.66.67)
    pub subnet_cidr: String,    // stock: 10.66.67.0/24
    // no_nat: the node owns forwarding, NAT and the firewall. Inert from 2.1.5 to 2.1.9 (the binary touched none of
    // it); 2.5 sets host-wide sysctls and installs FORWARD/MASQUERADE rules at start (tokio_io KernelNetworking),
    // which this flag now skips. Every swg-noded unit passes it.
    pub no_nat: bool,
    pub headless: bool,         // skip the web admin panel (binds 0.0.0.0), its TLS cert generation and
                                // reload loop, and the DPI/syscalls monitor sockets — the swg-panel owns
                                // everything via --desired, and the fixed loopback 46003/46004 collide on
                                // a multi-instance node. Stock false ⇒ unset behaves exactly like upstream.
    pub desired_path: Option<PathBuf>, // declarative desired-passwords file (panel-owned)
}

impl Default for InstanceCfg {
    fn default() -> Self {
        Self {
            iface: "csqtt1".to_owned(),
            gw_addr: "10.66.67.1".to_owned(),
            subnet_prefix: [10, 66, 67],
            subnet_cidr: "10.66.67.0/24".to_owned(),
            no_nat: false,
            headless: false,
            desired_path: None,
        }
    }
}

static CFG: OnceLock<InstanceCfg> = OnceLock::new();

pub fn init(cfg: InstanceCfg) {
    let _ = CFG.set(cfg);
}
pub fn get() -> &'static InstanceCfg {
    CFG.get_or_init(InstanceCfg::default)
}
pub fn iface() -> &'static str {
    &get().iface
}
pub fn gw_addr() -> &'static str {
    &get().gw_addr
}
pub fn subnet_cidr() -> &'static str {
    &get().subnet_cidr
}
pub fn subnet_prefix() -> [u8; 3] {
    get().subnet_prefix
}
// The address `last` of this instance's /24 (stock: 10.66.67.<last>).
pub fn subnet_addr(last: u8) -> String {
    let p = subnet_prefix();
    format!("{}.{}.{}.{last}", p[0], p[1], p[2])
}
pub fn no_nat() -> bool {
    get().no_nat
}
pub fn headless() -> bool {
    get().headless
}
pub fn desired_path() -> Option<&'static PathBuf> {
    get().desired_path.as_ref()
}

// Under --desired the node runs no IPv6 for csqtt (the TUN carries no v6 address and nothing routes it), so an IPv6
// uplink packet is dropped like a spoofed IPv4 source — stock passes any non-IPv4 packet unchecked.
pub fn ipv4_only_uplink() -> bool {
    desired_path().is_some()
}

// Build a config from the optional flags. Only /24 is accepted: the datapath route table indexes a session
// by the packet's last octet, so a differently-sized mask would need a different table shape.
pub fn build(
    iface: Option<String>,
    tun_addr: Option<String>,
    no_nat: bool,
    headless: bool,
    desired: Option<PathBuf>,
) -> anyhow::Result<InstanceCfg> {
    let mut cfg = InstanceCfg::default();
    if let Some(i) = iface
        && !i.is_empty()
    {
        cfg.iface = i;
    }
    if let Some(a) = tun_addr {
        let a = a.trim();
        if !a.is_empty() {
            let (ip_part, mask) = a.split_once('/').unwrap_or((a, "24"));
            if mask != "24" {
                anyhow::bail!("--tun-addr must be a /24 (got /{mask})");
            }
            let octs: Vec<u8> = ip_part
                .split('.')
                .map(|o| o.parse::<u8>())
                .collect::<Result<_, _>>()
                .map_err(|_| anyhow::anyhow!("bad --tun-addr {a}"))?;
            if octs.len() != 4 {
                anyhow::bail!("bad --tun-addr {a}");
            }
            cfg.subnet_prefix = [octs[0], octs[1], octs[2]];
            cfg.gw_addr = format!("{}.{}.{}.{}", octs[0], octs[1], octs[2], octs[3]);
            cfg.subnet_cidr = format!("{}.{}.{}.0/24", octs[0], octs[1], octs[2]);
        }
    }
    cfg.no_nat = no_nat;
    cfg.headless = headless;
    cfg.desired_path = desired;
    Ok(cfg)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn no_flags_is_stock() {
        let cfg = build(None, None, false, false, None).unwrap();
        assert_eq!(cfg.iface, "csqtt1");
        assert_eq!(cfg.gw_addr, "10.66.67.1");
        assert_eq!(cfg.subnet_prefix, [10, 66, 67]);
        assert_eq!(cfg.subnet_cidr, "10.66.67.0/24");
        assert!(!cfg.no_nat && !cfg.headless && cfg.desired_path.is_none());
    }

    #[test]
    fn tun_addr_sets_the_whole_subnet() {
        let cfg = build(Some("csqtt7".into()), Some("10.66.71.1/24".into()), true, true, None).unwrap();
        assert_eq!(cfg.iface, "csqtt7");
        assert_eq!(cfg.gw_addr, "10.66.71.1");
        assert_eq!(cfg.subnet_prefix, [10, 66, 71]);
        assert_eq!(cfg.subnet_cidr, "10.66.71.0/24");
        assert!(cfg.no_nat && cfg.headless);
        // a bare address means /24
        assert_eq!(build(None, Some("10.9.8.1".into()), false, false, None).unwrap().subnet_prefix, [10, 9, 8]);
    }

    #[test]
    fn only_a_24_is_accepted() {
        assert!(build(None, Some("10.66.71.1/16".into()), false, false, None).is_err());
        assert!(build(None, Some("10.66.71/24".into()), false, false, None).is_err());
        assert!(build(None, Some("10.66.300.1/24".into()), false, false, None).is_err());
    }
}
