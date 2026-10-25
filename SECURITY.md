# Security Policy

GeoWebCache is a project of the [GeoServer](https://geoserver.org) community. It does not have its own security
list: vulnerabilities are handled by the volunteers on the GeoServer security list, using the GeoServer
[security policy](https://github.com/geoserver/geoserver/blob/main/SECURITY.md), because most users run GeoWebCache
embedded in GeoServer.

## Supported Versions

GeoWebCache releases are made together with GeoServer, and a GeoWebCache release is supported for as long as the
GeoServer release that ships it.

| Version     | Supported          | Available               |
| ----------- | ------------------ |------------------------ |
| stable      | :white_check_mark: | six months              |
| maintenance | :white_check_mark: | twelve months           |
| archived    | :x:                |                         |

At the time of writing, the supported GeoWebCache series are 2.0.x (GeoServer 3.0.x), 1.28.x (GeoServer 2.28.x)
and 1.27.x (GeoServer 2.27.x). The GeoServer [release schedule](https://github.com/geoserver/geoserver/wiki/Release-Schedule)
and its security policy have the current list.

If your organization is making use of a version that is no longer in use by the community all is not lost.
You can volunteer on the developer list to make additional releases, or engage with one of the
[Commercial Support](http://geoserver.org/support/) providers.

## Reporting a Vulnerability

If you encounter a security vulnerability in GeoWebCache please take care to report in a responsible fashion:

1. Keep exploit details out of public forums, mailing lists, and issue tracker.

2. AI assistance to locate issues is allowed. Please read the [AI policy](https://geoserver.org/ai/) and make sure to follow it.
   A report needs a working reproduction against a supported version, and you are responsible for checking it is real
   before you send it.

   Be advised that GeoWebCache is a community project with a clear coordinated vulnerability disclosure
   policy allowing the members of our community to update prior to public disclosure.

   Reports linking to their own disclosure policy are not appropriate.

3. There are two options to report a security vulnerability:

   * To report via email:

     Please send an email directly to the volunteers on the private geoserver-security@lists.osgeo.org mailing list.
     Provide information about the security vulnerability you might have found in your email.

   * To report via GitHub:

     Navigate to the [security](https://github.com/GeoWebCache/geowebcache/security) page, use link for
     [Private vulnerability reporting](https://github.com/GeoWebCache/geowebcache/security/advisories/new).

     For more information see [GitHub documentation](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability#privately-reporting-a-security-vulnerability).

4. There is no expected response time. Be prepared to work with geoserver-security email list volunteers on a solution.

5. Keep in mind participants are volunteering their time, an extensive fix may require fundraising/resources.

For more information see [Community Support](http://geoserver.org/comm/).

## Coordinated vulnerability disclosure

Disclosure policy:

1. The reported vulnerability has been verified by working with the geoserver-security list
2. GitHub [security advisory](https://github.com/GeoWebCache/geowebcache/security) is used to reserve a CVE number
3. A fix or documentation clarification is accepted and backported to both the "stable" and "maintenance" branches
4. A fix is included for the "stable" and "maintenance" downloads ([released as scheduled](https://github.com/geoserver/geoserver/wiki/Release-Schedule), or issued via emergency update)
5. The CVE vulnerability is published with mitigation and patch instructions

This represents a balance between transparency and participation that does not overwhelm participants.
Those seeking greater visibility are encouraged to volunteer with the geoserver-security list;
or work with one of the [commercial support providers](https://geoserver.org/support/) who participate on behalf of their customers.

We advise security researchers and national agencies that linking to or attempting to enforce their own disclosure policies or timelines is unwelcome and inappropriate conduct.
The recommended course of action is to direct your clients to collaborate on, provide, or resource a fix.
